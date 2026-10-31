package vn.haui.dx.hpdi.intelligence.api;

public final class IntelligenceFailure extends RuntimeException {
    private final int status;
    private final String code;

    private IntelligenceFailure(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }
    public static IntelligenceFailure invalid() { return new IntelligenceFailure(502, "INTELLIGENCE_CONTRACT_FAILED", "Invalid Intelligence response"); }
    public static IntelligenceFailure configuration() { return new IntelligenceFailure(502, "INTELLIGENCE_INTEGRATION_FAILED", "Intelligence integration failed"); }
    public static IntelligenceFailure unavailable() { return new IntelligenceFailure(503, "INTELLIGENCE_UNAVAILABLE", "Intelligence unavailable; manual processing remains available"); }
    public static IntelligenceFailure timeout() { return new IntelligenceFailure(504, "INTELLIGENCE_TIMEOUT", "Intelligence deadline exceeded"); }
}
