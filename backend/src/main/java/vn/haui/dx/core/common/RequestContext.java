package vn.haui.dx.core.common;

import java.util.Optional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/** Lấy request ID của request hiện tại (trùng header {@code X-Request-ID} trả cho client). */
public final class RequestContext {
    public static final String REQUEST_ID_ATTRIBUTE = "requestId";

    private RequestContext() {}

    /** Rỗng khi không ở trong HTTP request, ví dụ job chạy nền. */
    public static Optional<String> requestId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) return Optional.empty();
        return Optional.ofNullable((String) attributes.getAttribute(REQUEST_ID_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST));
    }
}
