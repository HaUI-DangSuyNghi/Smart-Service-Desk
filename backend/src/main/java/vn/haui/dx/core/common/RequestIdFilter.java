package vn.haui.dx.core.common;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gắn request ID cho mọi request: nhận {@code X-Request-ID} từ gateway nếu đúng dạng UUID, không thì tự sinh.
 * Chạy trước Spring Security để lỗi 401/403 cũng có requestId; đưa vào MDC để log có requestId.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Request-ID";
    private static final Pattern UUID_TEXT = Pattern.compile("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String id = accept(request.getHeader(HEADER));
        request.setAttribute(RequestContext.REQUEST_ID_ATTRIBUTE, id);
        response.setHeader(HEADER, id);
        MDC.put(RequestContext.REQUEST_ID_ATTRIBUTE, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(RequestContext.REQUEST_ID_ATTRIBUTE);
        }
    }

    static String accept(String header) {
        if (header != null && UUID_TEXT.matcher(header).matches()) return header.toLowerCase(Locale.ROOT);
        return UUID.randomUUID().toString();
    }
}
