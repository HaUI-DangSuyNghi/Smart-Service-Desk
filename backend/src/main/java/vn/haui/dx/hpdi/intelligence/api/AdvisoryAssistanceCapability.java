package vn.haui.dx.hpdi.intelligence.api;

import java.util.Map;

/** Model advice without a claim of retrieval or verified citations. Caller authorizes context. */
public interface AdvisoryAssistanceCapability {
    AssistanceOutcome advise(String question, Map<String, String> context);
}
