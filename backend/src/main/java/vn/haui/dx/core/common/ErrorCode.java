package vn.haui.dx.core.common;

/** Mã lỗi trả trong trường {@code code} của body lỗi, kèm HTTP status tương ứng. */
public enum ErrorCode {
    VALIDATION_FAILED(400), UNAUTHENTICATED(401), FORBIDDEN(403), NOT_FOUND(404), CONFLICT(409),
    PAYLOAD_TOO_LARGE(413), UNSUPPORTED_MEDIA_TYPE(415), SERVICE_UNAVAILABLE(503);

    private final int status;
    ErrorCode(int status) { this.status = status; }
    public int status() { return status; }
}
