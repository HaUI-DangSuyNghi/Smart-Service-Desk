package vn.haui.dx.hpdi.intelligence.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.data.api.AuthorizedKnowledge;
import vn.haui.dx.hpdi.data.api.KnowledgeCatalog;
import vn.haui.dx.hpdi.human.api.PermissionGuard;
import vn.haui.dx.hpdi.human.api.Permissions;
import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;
import vn.haui.dx.hpdi.intelligence.integration.AssistanceTransport;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeChatControllerTest {
    private final ObjectMapper json = new ObjectMapper();
    private final UUID id = UUID.randomUUID();
    private AuthorizedKnowledge source() { return AuthorizedKnowledge.of(id, "lab-a", "Guide", "1", "a".repeat(64), "internal", "text/plain", "Check the power cable.".getBytes()); }
    @Test void authorizesBeforeAnyKnowledgeOrProviderAccess() {
        var knowledge = mock(KnowledgeCatalog.class); var permissions = mock(PermissionGuard.class); var transport = mock(AssistanceTransport.class);
        doThrow(CapabilityException.forbidden("Denied")).when(permissions).require("lab-a", Permissions.DOCUMENT_VIEW);
        var controller = new KnowledgeChatController(knowledge, permissions, transport, json);
        assertThrows(CapabilityException.class, () -> controller.chat("lab-a", new KnowledgeChatController.Question("Question", List.of())));
        verifyNoInteractions(knowledge, transport);
    }
    @Test void validatesExcerptsAndCitationNumbersBeforeClaimingGrounding() throws Exception {
        var state = new KnowledgeChatController.StreamState(Map.of(id, source()));
        state.accept(json.readTree("{\"type\":\"sources\",\"sources\":[{\"index\":1,\"metadataId\":\"" + id + "\",\"title\":\"Guide\",\"version\":\"1\",\"excerpt\":\"Check the power cable.\"}]}"));
        state.accept(json.readTree("{\"type\":\"delta\",\"text\":\"Check cable [1]\"}"));
        state.accept(json.readTree("{\"type\":\"done\",\"grounded\":true}"));
        assertThrows(IntelligenceFailure.class, () -> state.accept(json.readTree("{\"type\":\"delta\",\"text\":\"extra\"}")));
        var forged = new KnowledgeChatController.StreamState(Map.of(id, source()));
        assertThrows(IntelligenceFailure.class, () -> forged.accept(json.readTree("{\"type\":\"sources\",\"sources\":[{\"index\":1,\"metadataId\":\"" + id + "\",\"title\":\"Guide\",\"version\":\"1\",\"excerpt\":\"Fabricated text\"}]}")));
    }
    @Test void imageOnlyDocumentWithoutApprovedOcrNeverReachesProvider() {
        var knowledge = mock(KnowledgeCatalog.class); var permissions = mock(PermissionGuard.class); var transport = mock(AssistanceTransport.class);
        when(knowledge.resolve("lab-a", id)).thenReturn(AuthorizedKnowledge.of(id, "lab-a", "Scan", "1", "checksum", "internal", "application/pdf", new byte[0]));
        var controller = new KnowledgeChatController(knowledge, permissions, transport, json);
        assertThrows(CapabilityException.class, () -> controller.chat("lab-a", new KnowledgeChatController.Question("Question", List.of(id))));
        verifyNoInteractions(transport);
    }
    @Test void rejectsTextBeforeSourcesAndGroundingWithoutCitations() throws Exception {
        var state = new KnowledgeChatController.StreamState(Map.of(id, source()));
        assertThrows(IntelligenceFailure.class, () -> state.accept(json.readTree("{\"type\":\"delta\",\"text\":\"Unbound\"}")));
        state.accept(json.readTree("{\"type\":\"sources\",\"sources\":[]}"));
        state.accept(json.readTree("{\"type\":\"delta\",\"text\":\"No evidence\"}"));
        assertThrows(IntelligenceFailure.class, () -> state.accept(json.readTree("{\"type\":\"done\",\"grounded\":true}")));
        state.accept(json.readTree("{\"type\":\"done\",\"grounded\":false}"));
    }

    @Test void acceptsOnlyCanonicalRefusalWhenSourcesCannotAnswer() {
        for (var answer : List.of("Tài liệu đã chọn chưa có đủ thông tin để trả lời câu hỏi này.", "Bạn hãy cung cấp các phương án lựa chọn để tôi đối chiếu với tài liệu.", "Unsupported uncited answer")) {
            var state = new KnowledgeChatController.StreamState(Map.of(id, source()));
            state.accept(json.valueToTree(Map.of("type", "sources", "sources", List.of(Map.of("index", 1, "metadataId", id.toString(), "title", "Guide", "version", "1", "excerpt", "Check the power cable.")))));
            state.accept(json.valueToTree(Map.of("type", "delta", "text", answer)));
            var done = json.valueToTree(Map.of("type", "done", "grounded", false));
            if (answer.startsWith("Unsupported")) assertThrows(IntelligenceFailure.class, () -> state.accept(done));
            else assertDoesNotThrow(() -> state.accept(done));
        }
    }

    @Test void propagatesReaderIdentityIntoTransportCallbackThreads() throws Exception {
        var knowledge = mock(KnowledgeCatalog.class); var permissions = mock(PermissionGuard.class); var transport = mock(AssistanceTransport.class);
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new org.springframework.security.authentication.TestingAuthenticationToken("reader", "unused"));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
        try {
            doAnswer(invocation -> {
                assertEquals("reader", org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName());
                return null;
            }).when(permissions).require("lab-a", Permissions.DOCUMENT_VIEW);
            when(knowledge.resolve("lab-a", id)).thenReturn(source());
            doAnswer(invocation -> {
                java.util.function.Consumer<com.fasterxml.jackson.databind.JsonNode> events = invocation.getArgument(1);
                var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
                try {
                    executor.submit(() -> {
                        events.accept(json.valueToTree(Map.of("type", "sources", "sources", List.of(Map.of("index", 1, "metadataId", id.toString(), "title", "Guide", "version", "1", "excerpt", "Check the power cable.")))));
                        events.accept(json.valueToTree(Map.of("type", "delta", "text", "Check cable [1]")));
                        events.accept(json.valueToTree(Map.of("type", "done", "grounded", true)));
                    }).get();
                } catch (Exception error) { throw new AssertionError(error); }
                finally { executor.shutdownNow(); }
                return null;
            }).when(transport).streamKnowledge(any(), any());
            var controller = new KnowledgeChatController(knowledge, permissions, transport, json);
            var response = controller.chat("lab-a", new KnowledgeChatController.Question("Check power cable?", List.of(id)));
            assertEquals(org.springframework.http.MediaType.TEXT_EVENT_STREAM, response.getHeaders().getContentType());
            var output = new java.io.ByteArrayOutputStream();
            response.getBody().writeTo(output);
            var text = output.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(text.contains("\"type\":\"done\""), text);
            assertFalse(text.contains("\"type\":\"error\""));
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
}
