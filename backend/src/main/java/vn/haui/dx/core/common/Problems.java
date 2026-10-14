package vn.haui.dx.core.common;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Dựng body lỗi chuẩn (RFC 9457) có {@code code} và {@code requestId}; dùng chung cho Errors và Spring Security. */
public final class Problems {
    private Problems() {}

    public static ProblemDetail of(ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(code.status()), detail);
        problem.setProperty("code", code.name());
        RequestContext.requestId().ifPresent(id -> problem.setProperty("requestId", id));
        return problem;
    }
}
