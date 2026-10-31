package vn.haui.dx.apps.servicedesk.intelligence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import vn.haui.dx.apps.servicedesk.Ticket;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentPolicy;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.FactualSignalExtractor;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.ProblemAssessor;
import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;

import java.util.List;
import java.util.Map;

/** Model category and factual priority are independent; missing facts never erase a category. */
@Service
public final class BackgroundModelAssessment {
    private final LocalModelRuntime runtime;
    private final TicketClassificationOrchestrator classifier;
    private final ClassifierProcessingGate rights;
    private final AssessmentPolicy policy;
    private final ProblemAssessor facts;
    private final vn.haui.dx.hpdi.intelligence.api.IntelligenceCapability rules;
    private final TicketTriageProjection projection;

    public BackgroundModelAssessment(LocalModelRuntime runtime, TicketClassificationOrchestrator classifier,
            ClassifierProcessingGate rights, AssessmentPolicy policy, ObjectMapper json) {
        this(runtime, classifier, rights, policy, json, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public BackgroundModelAssessment(LocalModelRuntime runtime, TicketClassificationOrchestrator classifier,
            ClassifierProcessingGate rights, AssessmentPolicy policy, ObjectMapper json,
            vn.haui.dx.hpdi.intelligence.api.IntelligenceCapability rules) {
        this.runtime = runtime;
        this.classifier = classifier;
        this.rights = rights;
        this.policy = policy;
        this.rules = rules;
        this.projection = new TicketTriageProjection(json);
        try {
            var local = json.readTree(AssessmentPolicy.read("local-factual-signals-v0.1.json"));
            var configuration = (ObjectNode) policy.structuredExtraction();
            configuration.put("deviceIdentifierPattern", local.path("deviceIdentifierPattern").asText());
            configuration.put("eventPattern", local.path("eventPattern").asText());
            facts = new ProblemAssessor(policy, new FactualSignalExtractor(configuration));
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot load local factual extraction", failure);
        }
    }

    public boolean enabled() { return runtime.enabled(); }
    public boolean automaticWorkspaces() { return runtime.automaticWorkspaces(); }
    public boolean supports(String workspace) { return runtime.supports(workspace); }
    public void requireScope(String workspace) { runtime.authorize(workspace, "BACKGROUND", classifier.modelDigest()); }

    public StoredAssessment.Result assess(Ticket ticket) {
        policy.verifyUnchanged();
        return classifier.withBackgroundSnapshot(ticket.workspace(), ticket.id(),
                (snapshot, model) -> {
                    // Both sources see the same authorized snapshot; the orchestrator rechecks it after both calls.
                    TicketIntelligenceView.Triage ruleVote = null;
                    try {
                        var suggestion = rules.suggest(snapshot.workspace(), new vn.haui.dx.hpdi.intelligence.api.TriageInput(
                                "service-desk", "0.3.0", snapshot.title(), snapshot.description()));
                        ruleVote = projection.project(suggestion, snapshot);
                    } catch (IntelligenceFailure unavailable) {
                        // Preserve explicit source availability without changing the other source's conclusion.
                    }
                    return cooperate(compose(snapshot, model), ruleVote);
                },
                () -> rights.verifyAssessment(ticket.workspace(), policy.bundleDigest()));
    }

    StoredAssessment.Result compose(Ticket ticket, TicketClassificationView model) {
        boolean unavailable = "DEGRADED".equals(model.status());
        var reasons = model.policyDisposition().reasonCodes();
        boolean unsafe = reasons.contains("SAFETY_GATE") || reasons.contains("HARD_EXCLUSION");
        String category = unsafe || unavailable || model.classification() == null ? null : model.classification().category();
        var assessment = facts.assess(Map.of("title", ticket.title(), "description", ticket.description()));
        var questions = new java.util.ArrayList<String>();
        if (category == null) questions.add("Thiết bị hoặc dịch vụ nào bị lỗi? Bạn mô tả thêm triệu chứng cụ thể được không?");
        if (!unsafe && assessment.impact().value() == null) questions.add(policy.question("impact"));
        if (!unsafe && assessment.urgency().value() == null) questions.add(policy.question("urgency"));
        var evidence = unsafe ? List.<TicketIntelligenceView.InputEvidence>of() : assessment.evidence().stream()
                .map(span -> new TicketIntelligenceView.InputEvidence(span.field(), span.quote())).distinct().toList();
        if (evidence.size() > 64) throw IntelligenceFailure.invalid();
        String priority = unsafe ? null : assessment.priority();
        String classificationReason = unsafe ? "Nội dung cần người xử lý kiểm tra trực tiếp."
                : unavailable ? "Model tạm không khả dụng."
                : category == null ? "Model chưa xác định được loại sự cố; cần bổ sung thông tin."
                : "Model gợi ý nhóm " + category + "; người xử lý cần xác nhận.";
        String priorityReason = priority == null ? "Chưa đủ dữ kiện xác nhận mức ưu tiên; không tự gán mức mặc định."
                : "Mức " + priority + " theo tác động " + assessment.impact().value() + " và độ khẩn " + assessment.urgency().value() + ".";
        return new StoredAssessment.Result("0.1.0", "service-desk-local@1.0.0", "model",
                "servicedesk-local-facts@0.1.0", classifier.modelDigest(), category, priority,
                unsafe ? null : assessment.impact().value(), unsafe ? null : assessment.urgency().value(),
                unsafe ? "MODEL_REVIEW_REQUIRED" : unavailable ? "MODEL_UNAVAILABLE" : category == null ? "MODEL_ABSTAINED" : "MODEL_CLASSIFIED",
                evidence, new TicketIntelligenceView.Explanation(classificationReason, priorityReason), true, questions);
    }

    StoredAssessment.Result cooperate(StoredAssessment.Result model, TicketIntelligenceView.Triage rules) {
        boolean ruleAvailable = rules != null && "SUCCESS".equals(rules.status());
        boolean ruleClassified = ruleAvailable && !"INSUFFICIENT_INFORMATION".equals(rules.reasonCode());
        boolean unsafe = "MODEL_REVIEW_REQUIRED".equals(model.reasonCode());
        String ruleCategory = ruleClassified ? rules.category() : null;
        boolean conflict = model.category() != null && ruleCategory != null && !model.category().equals(ruleCategory);
        String category = unsafe || conflict ? null : model.category() != null ? model.category() : ruleCategory;
        String impact = unsafe ? null : axis(model.impact(), ruleClassified ? rules.impact() : null);
        String urgency = unsafe ? null : axis(model.urgency(), ruleClassified ? rules.urgency() : null);
        String priority = policy.priority(impact, urgency);
        String modelStatus = "MODEL_UNAVAILABLE".equals(model.reasonCode()) ? "UNAVAILABLE"
                : unsafe ? "BLOCKED" : model.category() == null ? "ABSTAINED" : "AVAILABLE";
        String ruleStatus = !ruleAvailable ? "UNAVAILABLE" : ruleClassified ? "CLASSIFIED" : "INSUFFICIENT_INFORMATION";
        String decision = unsafe ? "MANUAL_REVIEW" : conflict ? "CONFLICT"
                : model.category() != null && ruleCategory != null ? "AGREEMENT"
                : category == null ? "INSUFFICIENT_INFORMATION" : model.category() != null ? "MODEL_SUPPORTED" : "RULES_SUPPORTED";
        var evidence = new java.util.LinkedHashSet<>(model.evidence());
        if (!unsafe && ruleAvailable) evidence.addAll(rules.inputEvidence());
        if (evidence.size() > 64) throw IntelligenceFailure.invalid();
        var questions = new java.util.ArrayList<String>();
        if (conflict) questions.add("Model và bộ luật đề xuất khác nhau. Điều phối viên cần xác nhận loại sự cố.");
        else if (category == null) questions.add("Bạn mô tả thêm thiết bị hoặc dịch vụ và triệu chứng bị lỗi được không?");
        if (!unsafe && impact == null) questions.add(policy.question("impact"));
        if (!unsafe && urgency == null) questions.add(policy.question("urgency"));
        String explanation = unsafe ? "Nội dung cần người xử lý kiểm tra trực tiếp."
                : conflict ? "Model và bộ luật có kết quả khác nhau; chưa chốt loại sự cố."
                : "UNAVAILABLE".equals(modelStatus) ? "Model tạm không khả dụng; kết quả bộ luật vẫn được giữ để hỗ trợ xử lý."
                : "AGREEMENT".equals(decision) ? "Model và bộ luật cùng đề xuất một loại sự cố; người xử lý cần xác nhận."
                : "RULES_SUPPORTED".equals(decision) ? "Model chưa kết luận; bộ luật có gợi ý phân loại cần người xử lý xác nhận."
                : "MODEL_SUPPORTED".equals(decision) ? "Model có gợi ý phân loại; bộ luật chưa đủ dữ kiện để kết luận đầy đủ."
                : "Model và bộ luật chưa đủ dữ kiện phân loại.";
        String priorityReason = priority == null ? "Chưa đủ dữ kiện xác nhận mức ưu tiên; không tự gán mức mặc định."
                : "Ưu tiên theo ma trận tác động " + impact + " và độ khẩn " + urgency + "; người xử lý cần xác nhận.";
        return new StoredAssessment.Result(model.contractVersion(), "service-desk-hybrid@1.0.0", "hybrid",
                "0.3.0+local-facts@0.1.0", model.modelVersion(), category, priority, impact, urgency,
                unsafe ? "MODEL_REVIEW_REQUIRED" : conflict ? "HYBRID_CONFLICT" : category == null ? "MODEL_ABSTAINED"
                        : "RULES_SUPPORTED".equals(decision) ? "RULES_SUPPORTED" : "MODEL_CLASSIFIED",
                List.copyOf(evidence), new TicketIntelligenceView.Explanation(explanation, priorityReason), true, questions,
                new StoredAssessment.Cooperation(runtime.providerLabel(), model.category(), ruleCategory, modelStatus, ruleStatus, decision));
    }

    private static String axis(String modelFacts, String ruleFacts) {
        return modelFacts == null ? ruleFacts : ruleFacts == null || modelFacts.equals(ruleFacts) ? modelFacts : null;
    }
}
