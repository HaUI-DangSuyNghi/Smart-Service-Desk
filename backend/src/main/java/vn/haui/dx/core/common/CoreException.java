package vn.haui.dx.core.common;

/**
 * Lỗi nghiệp vụ duy nhất các module ném ra; {@code Errors} đổi thành body lỗi có {@code code} và {@code requestId}.
 * Message hiện cho client: không chứa SQL, token, stack trace.
 */
public class CoreException extends RuntimeException {
    private final ErrorCode code;

    public CoreException(ErrorCode code, String detail) { super(detail); this.code = code; }
    public ErrorCode code() { return code; }

    public static CoreException validation(String detail) { return new CoreException(ErrorCode.VALIDATION_FAILED, detail); }
    public static CoreException forbidden(String detail) { return new CoreException(ErrorCode.FORBIDDEN, detail); }
    public static CoreException notFound(String detail) { return new CoreException(ErrorCode.NOT_FOUND, detail); }
    public static CoreException conflict(String detail) { return new CoreException(ErrorCode.CONFLICT, detail); }
}
