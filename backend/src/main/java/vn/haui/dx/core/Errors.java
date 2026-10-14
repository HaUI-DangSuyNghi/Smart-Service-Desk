package vn.haui.dx.core;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.common.RequestContext;
import static org.springframework.http.HttpStatus.CONFLICT;

@RestControllerAdvice
public class Errors {
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflict() { return ProblemDetail.forStatusAndDetail(CONFLICT, "Resource already exists or violates a constraint"); }

    @ExceptionHandler(CoreException.class)
    public ProblemDetail core(CoreException error) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(error.code().status()), error.getMessage());
        problem.setProperty("code", error.code().name());
        RequestContext.requestId().ifPresent(id -> problem.setProperty("requestId", id));
        return problem;
    }
}
