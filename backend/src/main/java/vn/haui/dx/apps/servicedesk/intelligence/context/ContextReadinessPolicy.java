package vn.haui.dx.apps.servicedesk.intelligence.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentPolicy;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.ProblemAssessor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static vn.haui.dx.apps.servicedesk.intelligence.context.ContextReadinessView.State.*;

@Component
public final class ContextReadinessPolicy {
    private final JsonNode policy;
    private final String digest;
    private final Pattern account;
    private final ProblemAssessor assessor;

    public ContextReadinessPolicy(ObjectMapper json, ProblemAssessor assessor) {
        this.assessor = assessor;
        byte[] raw = AssessmentPolicy.read("readiness-policy-v0.1.json");
        digest = ContextPolicyCatalog.digest(raw);
        try {
            policy = json.readTree(raw);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot read readiness policy", failure);
        }
        if (!"servicedesk-readiness@0.1.0".equals(policy.path("version").asText())
                || !"DRAFT".equals(policy.path("approvalStatus").asText())
                || policy.path("workflowGateApplied").asBoolean(true)
                || maxRounds() < 1 || maxRounds() > 2 || maxQuestions() != 3
                || waitSeconds() < 60 || waitSeconds() > 86400
                || reminderSeconds() < 1 || reminderSeconds() >= waitSeconds()
                || highWaitSeconds() < 60 || highWaitSeconds() > 1800) {
            throw new IllegalArgumentException("Unsupported readiness policy");
        }
        account = Pattern.compile(policy.path("accountPattern").asText(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    public String digest() { return digest; }
    public int maxRounds() { return policy.path("maxRounds").asInt(); }
    public int maxQuestions() { return policy.path("maxQuestions").asInt(); }
    public long waitSeconds() { return policy.path("waitSeconds").asLong(); }
    public long reminderSeconds() { return policy.path("reminderSeconds").asLong(); }
    public long highWaitSeconds() { return policy.path("highWaitSeconds").asLong(); }
    public long debounceMillis() { return policy.path("debounceMillis").asLong(); }

    public void verifyUnchanged() {
        if (!digest.equals(ContextPolicyCatalog.digest(AssessmentPolicy.read("readiness-policy-v0.1.json")))) {
            throw new IllegalStateException("Readiness policy changed");
        }
    }

    public ContextReadinessView evaluate(ContextSnapshot snapshot, ContextProjection context, int rounds) {
        var reasons = new LinkedHashSet<String>();
        boolean urgent = false;
        boolean accountIssue = false;
        for (var source : snapshot.sources()) {
            var facts = assessor.assess(Map.of("title", source.text()));
            if ("U4".equals(facts.urgency().value())) {
                reasons.add("CRITICAL_ESCALATION");
            }
            for (var flag : facts.risk().flags()) {
                if (List.of("SAFETY", "SECURITY_EXPOSURE", "DATA_LOSS").contains(flag)) {
                    reasons.add("SAFETY_ESCALATION");
                }
            }
            urgent |= "U3".equals(facts.urgency().value());
            accountIssue |= source.slot() == null && account.matcher(source.text()).find();
        }
        if (urgent) {
            reasons.add("HIGH_URGENCY_REVIEW");
        }

        var conflicts = context.slots().entrySet().stream()
                .filter(entry -> List.of("CONFLICT", "AMBIGUOUS").contains(entry.getValue().status()))
                .map(Map.Entry::getKey).toList();
        boolean assessmentPossible = accountIssue || context.usable("device.type") || context.usable("symptomCodes");
        var assessmentMissing = new ArrayList<String>();
        if (!assessmentPossible) {
            assessmentMissing.add("device.type");
            assessmentMissing.add("symptomCodes");
        }
        assessmentMissing.addAll(conflicts);
        var duplicateMissing = new ArrayList<String>();
        for (String slot : List.of("location.room", "device.type", "device.identifier", "symptomCodes")) {
            if (!context.usable(slot)) {
                duplicateMissing.add(slot);
            }
        }
        // Category đã duyệt chưa có ở baseline; không nhận category do client/model tự gắn verified.
        duplicateMissing.add("verifiedCategory");
        var questions = new ArrayList<ContextReadinessView.Question>();
        conflicts.forEach(slot -> addQuestion(questions, slot, true));
        assessmentMissing.stream().filter(slot -> !conflicts.contains(slot)).forEach(slot -> addQuestion(questions, slot, false));
        if (!accountIssue) {
            duplicateMissing.stream().filter(slot -> !"verifiedCategory".equals(slot))
                    .forEach(slot -> addQuestion(questions, slot, false));
        }
        var overall = !assessmentMissing.isEmpty() ? ENRICHMENT_REQUIRED
                : questions.isEmpty() ? READINESS_OK : ENRICHMENT_OPTIONAL;
        if (!conflicts.isEmpty()) {
            reasons.add("CONFLICTING_USER_REPORTS");
        }
        if (!assessmentMissing.isEmpty()) {
            reasons.add("MISSING_REQUIRED_SLOT");
        }
        reasons.add("DUPLICATE_CHECK_UNREADY");
        if (rounds >= maxRounds() && !questions.isEmpty()) {
            reasons.add("MAX_CLARIFICATION_ROUNDS");
        }
        if (reasons.contains("SAFETY_ESCALATION") || reasons.contains("CRITICAL_ESCALATION")
                || reasons.contains("MAX_CLARIFICATION_ROUNDS")) {
            overall = MANUAL_REVIEW_REQUIRED;
            questions.clear();
        }
        String action = switch (overall) {
            case READINESS_OK -> "CONTINUE_ADVISORY";
            case ENRICHMENT_OPTIONAL -> "ASK_AND_CONTINUE_UNBLOCKED";
            case ENRICHMENT_REQUIRED -> "ANSWER_CLARIFICATION";
            case MANUAL_REVIEW_REQUIRED -> "COORDINATOR_REVIEW";
        };
        var tasks = List.of(
                new ContextReadinessView.Task("ASSESSMENT", assessmentMissing.isEmpty() ? READINESS_OK : ENRICHMENT_REQUIRED,
                        assessmentMissing, assessmentMissing.isEmpty() ? List.of() : List.of("ASSESSMENT_PROPOSAL")),
                new ContextReadinessView.Task("DUPLICATE_CHECK", ENRICHMENT_REQUIRED,
                        duplicateMissing, List.of("EXACT_DUPLICATE_ASSERTION")));
        return new ContextReadinessView(ContextReadinessView.VERSION, snapshot.ticketId(), snapshot.contextRevision(),
                snapshot.digest(), overall, tasks, List.copyOf(reasons), action, questions.stream().limit(3).toList(),
                digest, true, false, false);
    }

    private void addQuestion(List<ContextReadinessView.Question> questions, String slot, boolean conflict) {
        if (questions.stream().anyMatch(question -> question.slot().equals(slot))) {
            return;
        }
        String label = switch (slot) {
            case "location.room" -> "phòng xảy ra sự cố";
            case "device.identifier" -> "mã thiết bị";
            case "device.type" -> "loại thiết bị hoặc dịch vụ";
            default -> "phạm vi hoặc biểu hiện sự cố";
        };
        String text = conflict ? "Thông tin về " + label + " chưa thống nhất. Bạn xác nhận lại giá trị đúng giúp nhé."
                : policy.path("questions").path(slot).asText();
        if (text.isBlank()) {
            return;
        }
        String id = ContextPolicyCatalog.digest("readiness:" + slot + ":" + conflict + ":" + digest).substring(0, 24);
        questions.add(new ContextReadinessView.Question(id, slot, text, List.of(), conflict));
    }
}
