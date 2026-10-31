package vn.haui.dx.hpdi.intelligence.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.common.api.PageRequest;
import vn.haui.dx.hpdi.data.api.AuthorizedKnowledge;
import vn.haui.dx.hpdi.data.api.KnowledgeCatalog;
import vn.haui.dx.hpdi.human.api.PermissionGuard;
import vn.haui.dx.hpdi.human.api.Permissions;
import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;
import vn.haui.dx.hpdi.intelligence.integration.AssistanceTransport;

@RestController
@RequestMapping("/api/hpdi/v1/workspaces/{workspace}/knowledge")
public class KnowledgeChatController {
    private final KnowledgeCatalog knowledge;
    private final PermissionGuard permissions;
    private final AssistanceTransport transport;
    private final ObjectMapper json;
    private final java.util.concurrent.Semaphore preparation = new java.util.concurrent.Semaphore(4);
    public KnowledgeChatController(KnowledgeCatalog knowledge, PermissionGuard permissions,
            AssistanceTransport transport, ObjectMapper json) {
        this.knowledge = knowledge; this.permissions = permissions; this.transport = transport; this.json = json;
    }
    public record Question(String question, List<UUID> documentIds) {}

    @PostMapping(value = "/chat", produces = "text/event-stream")
    public ResponseEntity<StreamingResponseBody> chat(@PathVariable String workspace, @RequestBody Question input) {
        permissions.require(workspace, Permissions.DOCUMENT_VIEW);
        if (input == null || input.question() == null || input.question().isBlank() || input.question().length() > 2000
                || input.question().codePoints().anyMatch(value -> Character.isISOControl(value) && value != '\n' && value != '\r' && value != '\t')
                || input.documentIds() == null || input.documentIds().size() > 50 || input.documentIds().stream().anyMatch(java.util.Objects::isNull))
            throw CapabilityException.validation("Question must have 1-2000 characters and at most 50 document IDs.");
        if (!preparation.tryAcquire()) throw IntelligenceFailure.unavailable();
        try { return prepare(workspace, input); }
        finally { preparation.release(); }
    }

    private ResponseEntity<StreamingResponseBody> prepare(String workspace, Question input) {
        var ids = input.documentIds();
        if (ids.isEmpty()) {
            var page = knowledge.search(workspace, null, new PageRequest(0, 50));
            if (page.total() > 50) throw CapabilityException.validation("There are more than 50 documents. Select a document to narrow the search.");
            ids = page.items().stream().map(item -> item.id()).toList();
        }
        if (new HashSet<>(ids).size() != ids.size()) throw CapabilityException.validation("Duplicate document IDs");
        var request = json.createObjectNode().put("question", input.question().trim());
        var documents = request.putArray("documents");
        Map<UUID, AuthorizedKnowledge> sources = new HashMap<>();
        int total = 0;
        for (var id : ids) {
            var source = knowledge.resolve(workspace, id);
            if (source.text().isBlank()) continue;
            total += source.text().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (total > 1_000_000) throw CapabilityException.validation("Selected documents are too large. Select a single document.");
            sources.put(id, source);
            documents.addObject().put("id", id.toString()).put("title", source.title()).put("version", source.version()).put("text", source.text());
        }
        if (!ids.isEmpty() && documents.isEmpty()) throw CapabilityException.validation("Selected documents have no readable text. Use a PDF with selectable text, DOCX, TXT or Markdown.");
        var context = org.springframework.security.core.context.SecurityContextHolder.getContext();
        StreamingResponseBody stream = output -> new DelegatingSecurityContextRunnable(() -> {
            var state = new StreamState(sources);
            try {
                emit(output, Map.of("type", "status", "text", "Searching authorized documents"));
                transport.streamKnowledge(request, event -> new DelegatingSecurityContextRunnable(() -> {
                    permissions.require(workspace, Permissions.DOCUMENT_VIEW);
                    state.accept(event);
                    emit(output, event);
                }, context).run());
                if (!state.done) throw IntelligenceFailure.invalid();
            } catch (Exception error) {
                emit(output, Map.of("type", "error", "code", "KNOWLEDGE_UNAVAILABLE"));
            }
        }, context).run();
        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.TEXT_EVENT_STREAM).header("Cache-Control", "no-store").header("X-Accel-Buffering", "no")
                .header("X-Content-Type-Options", "nosniff").body(stream);
    }

    private void emit(OutputStream output, Object event) {
        try {
            output.write("data: ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            output.write(json.writeValueAsBytes(event));
            output.write("\n\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            output.flush();
        } catch (IOException error) { throw new UncheckedIOException(error); }
    }

    static final class StreamState {
        private final Map<UUID, AuthorizedKnowledge> documents;
        private boolean sourcesSeen, done;
        private int sourceCount, characters;
        private final StringBuilder answer = new StringBuilder();
        StreamState(Map<UUID, AuthorizedKnowledge> documents) { this.documents = documents; }
        void accept(JsonNode event) {
            if (event == null || !event.isObject() || done) throw IntelligenceFailure.invalid();
            switch (event.path("type").asText()) {
                case "sources" -> {
                    var citations = event.path("sources");
                    if (sourcesSeen || !citations.isArray() || citations.size() > 5) throw IntelligenceFailure.invalid();
                    sourcesSeen = true;
                    sourceCount = citations.size();
                    int index = 0;
                    for (var citation : citations) {
                        var source = documents.get(UUID.fromString(citation.path("metadataId").asText()));
                        var excerpt = citation.path("excerpt").asText();
                        if (source == null || !citation.path("index").isInt() || citation.path("index").intValue() != ++index
                                || !source.title().equals(citation.path("title").asText()) || !source.version().equals(citation.path("version").asText())
                                || excerpt.isBlank() || excerpt.length() > 2400 || !source.text().contains(excerpt)) throw IntelligenceFailure.invalid();
                    }
                }
                case "delta" -> {
                    if (!sourcesSeen || !event.path("text").isTextual()) throw IntelligenceFailure.invalid();
                    var text = event.path("text").textValue();
                    characters += text.length();
                    if (characters > 20000 || text.codePoints().anyMatch(value -> Character.isISOControl(value) && value != '\n' && value != '\r' && value != '\t'))
                        throw IntelligenceFailure.invalid();
                    answer.append(text);
                }
                case "done" -> {
                    if (!sourcesSeen || answer.toString().isBlank() || !event.path("grounded").isBoolean()
                            || (event.path("grounded").booleanValue() && sourceCount == 0)
                            || (!event.path("grounded").booleanValue() && sourceCount > 0
                                && !java.util.Set.of("Tài liệu đã chọn chưa có đủ thông tin để trả lời câu hỏi này.",
                                        "Bạn hãy cung cấp các phương án lựa chọn để tôi đối chiếu với tài liệu.").contains(answer.toString().strip()))) throw IntelligenceFailure.invalid();
                    if (event.path("grounded").booleanValue()) {
                        var matcher = java.util.regex.Pattern.compile("\\[(\\d+)\\]").matcher(answer);
                        boolean cited = false;
                        while (matcher.find()) {
                            int index = Integer.parseInt(matcher.group(1));
                            if (index < 1 || index > sourceCount) throw IntelligenceFailure.invalid();
                            cited = true;
                        }
                        if (!cited) throw IntelligenceFailure.invalid();
                    }
                    done = true;
                }
                default -> throw IntelligenceFailure.invalid();
            }
        }
    }
}
