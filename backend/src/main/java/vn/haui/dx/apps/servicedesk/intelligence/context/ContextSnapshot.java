package vn.haui.dx.apps.servicedesk.intelligence.context;

import java.util.List;
import java.util.UUID;

public record ContextSnapshot(
        String workspace, UUID ticketId, long contextRevision, List<Source> sources, String digest) {

    public ContextSnapshot {
        sources = List.copyOf(sources);
        if (sources.size() > 258 || contextRevision < 0 || sources.stream().anyMatch(
                source -> source.text() == null || source.text().length() > 4000
                        || source.revision() < 0 || source.revision() > contextRevision
                        || source.sourceId() == null || !source.sourceId().matches(
                                "title|description|clarification:[0-9a-f-]{36}:[0-7]"))
                || sources.stream().map(Source::sourceId).distinct().count() != sources.size()) {
            throw new IllegalArgumentException("Context snapshot exceeds budget");
        }
    }

    public record Source(String sourceId, long revision, String text, String slot, boolean correction) {}
}
