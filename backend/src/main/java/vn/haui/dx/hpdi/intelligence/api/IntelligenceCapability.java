package vn.haui.dx.hpdi.intelligence.api;
/** The adapter derives actor, workspace and request correlation; callers cannot grant trusted context. */
public interface IntelligenceCapability {
    TriageSuggestion suggest(String workspace, TriageInput input);
}
