package vn.haui.dx.apps.servicedesk.intelligence.taxonomy;

import org.springframework.stereotype.Component;

import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentEvidence;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.LiteralSignals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public final class SubcategoryResolver {
    public record Resolution(
            String child,
            String status,
            List<AssessmentEvidence> evidence,
            List<String> relatedParents) {
        public Resolution {
            evidence = List.copyOf(evidence);
            relatedParents = List.copyOf(relatedParents);
        }
    }

    private final TaxonomyCatalog taxonomy;

    public SubcategoryResolver(TaxonomyCatalog taxonomy) {
        this.taxonomy = taxonomy;
    }

    public Resolution resolve(String parent, Map<String, String> fields) {
        if (parent == null || !TaxonomyCatalog.PARENTS.contains(parent)) {
            return new Resolution(null, "NOT_ASSESSED", List.of(), List.of());
        }
        var candidates = new ArrayList<String>();
        var evidence = new ArrayList<AssessmentEvidence>();
        var relatedParents = new java.util.TreeSet<String>();
        var foreignEvidence = new ArrayList<AssessmentEvidence>();
        for (var child : taxonomy.children()) {
            if (!child.active()) {
                continue;
            }
            var spans =
                    LiteralSignals.find(
                            fields, child.phrases(), "subCategory", "CHILD-" + child.id());
            if (spans.isEmpty()) {
                continue;
            }
            if (parent.equals(child.parent())) {
                evidence.addAll(spans);
                candidates.add(child.id());
            } else {
                relatedParents.add(child.parent());
                foreignEvidence.addAll(spans);
            }
        }
        if (candidates.isEmpty() && !relatedParents.isEmpty()) {
            return new Resolution(
                    null, "TAXONOMY_CONFLICT", foreignEvidence, List.copyOf(relatedParents));
        }
        if (candidates.size() > 1) {
            return new Resolution(null, "AMBIGUOUS", evidence, List.copyOf(relatedParents));
        }
        return candidates.isEmpty()
                ? new Resolution(null, "INSUFFICIENT_EVIDENCE", evidence, List.of())
                : new Resolution(
                        candidates.get(0),
                        "SUPPORTED_BY_RULE",
                        evidence,
                        List.copyOf(relatedParents));
    }
}
