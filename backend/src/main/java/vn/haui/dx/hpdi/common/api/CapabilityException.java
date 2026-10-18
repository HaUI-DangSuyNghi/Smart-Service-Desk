package vn.haui.dx.hpdi.common.api;

/** Stable application-facing error categories; no engine or transport types. */
public class CapabilityException extends RuntimeException {
    public enum Code { VALIDATION_FAILED, FORBIDDEN, NOT_FOUND, CONFLICT }
    private final Code code;
    public CapabilityException(Code code, String detail) { super(detail); this.code = code; }
    public Code code() { return code; }
    public static CapabilityException validation(String detail) { return new CapabilityException(Code.VALIDATION_FAILED, detail); }
    public static CapabilityException forbidden(String detail) { return new CapabilityException(Code.FORBIDDEN, detail); }
    public static CapabilityException notFound(String detail) { return new CapabilityException(Code.NOT_FOUND, detail); }
    public static CapabilityException conflict(String detail) { return new CapabilityException(Code.CONFLICT, detail); }
}
