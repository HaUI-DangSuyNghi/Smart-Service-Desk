package vn.haui.dx.apps.servicedesk.triage;

public final class TriageFailure extends RuntimeException {
    private final int status;
    private final String code;

    private TriageFailure(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }
    public static TriageFailure invalid() { return new TriageFailure(502, "INTELLIGENCE_CONTRACT_FAILED", "Invalid Intelligence response"); }
    public static TriageFailure configuration() { return new TriageFailure(502, "INTELLIGENCE_INTEGRATION_FAILED", "Intelligence integration failed"); }
    public static TriageFailure unavailable() { return new TriageFailure(503, "INTELLIGENCE_UNAVAILABLE", "Intelligence unavailable; manual processing remains available"); }
    public static TriageFailure timeout() { return new TriageFailure(504, "INTELLIGENCE_TIMEOUT", "Intelligence deadline exceeded"); }
}
