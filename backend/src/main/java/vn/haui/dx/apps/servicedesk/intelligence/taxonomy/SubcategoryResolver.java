package vn.haui.dx.apps.servicedesk.intelligence.taxonomy;

import org.springframework.stereotype.Component;

import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentEvidence;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.LiteralSignals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public final class SubcategoryResolver {
    public record Resolution(String child, String status, List<AssessmentEvidence> evidence) {
        public Resolution {
            evidence = List.copyOf(evidence);
        }
    }

    private final TaxonomyCatalog taxonomy;

    public SubcategoryResolver(TaxonomyCatalog taxonomy) {
        this.taxonomy = taxonomy;
    }

    public Resolution resolve(String parent, Map<String, String> fields) {
        if (parent == null || !TaxonomyCatalog.PARENTS.contains(parent)) {
            return new Resolution(null, "NOT_ASSESSED", List.of());
        }
        var candidates = new ArrayList<String>();
        var evidence = new ArrayList<AssessmentEvidence>();
        boolean conflict = false;
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
            evidence.addAll(spans);
            if (parent.equals(child.parent())) {
                candidates.add(child.id());
            } else {
                conflict = true;
            }
        }
        if (conflict) {
            return new Resolution(null, "TAXONOMY_CONFLICT", evidence);
        }
        if (candidates.size() > 1) {
            return new Resolution(null, "AMBIGUOUS", evidence);
        }
        return candidates.isEmpty()
                ? new Resolution(null, "INSUFFICIENT_EVIDENCE", evidence)
                : new Resolution(candidates.get(0), "SUPPORTED_BY_RULE", evidence);
    }
}
