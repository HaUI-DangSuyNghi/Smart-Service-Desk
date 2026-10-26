package vn.haui.dx.apps.servicedesk.intelligence;

import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentEvidence;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.ProblemAssessor;

import java.util.List;

public record TicketAssessmentView(
        String contractVersion,
        String mode,
        String status,
        Classification classification,
        Assessment assessment,
        List<Attribute> attributes,
        List<AssessmentEvidence> evidence,
        List<String> missingInfo,
        List<String> questions,
        Disposition disposition,
        Provenance provenance) {
    public static final String VERSION = "ticket-intelligence-assessment@0.2.0";

    public TicketAssessmentView {
        attributes = List.copyOf(attributes);
        evidence = List.copyOf(evidence);
        missingInfo = List.copyOf(missingInfo);
        questions = List.copyOf(questions);
    }

    public record Classification(
            String decision,
            String mainCategory,
            String subCategory,
            String subCategoryStatus,
            List<String> alternatives,
            Double confidence,
            String confidenceSemantics) {
        public Classification {
            alternatives = List.copyOf(alternatives);
        }
    }

    public record Assessment(
            String impact, String urgency, String priority, ProblemAssessor.Risk risk) {}

    public record Attribute(String slot, String value, AssessmentEvidence evidence) {}

    public record Disposition(
            boolean needsReview,
            boolean operationalAuthority,
            boolean autoRoutingAllowed,
            List<String> reasons) {
        public Disposition {
            reasons = List.copyOf(reasons);
        }
    }

    public record Provenance(
            String modelDigest,
            String modelPolicyDigest,
            String taxonomyVersion,
            String taxonomyDigest,
            String assessmentPolicyVersion,
            String assessmentPolicyDigest,
            String priorityPolicyDigest,
            String policyApprovalStatus,
            String contractVersion) {}
}
