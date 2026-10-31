package vn.haui.dx.hpdi.intelligence.web;

import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.haui.dx.core.common.RequestContext;

@RestControllerAdvice
public class IntelligenceErrors {
    @ExceptionHandler(IntelligenceFailure.class)
    public ProblemDetail failure(IntelligenceFailure failure) {
        var problem = ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatusCode.valueOf(failure.status()), failure.getMessage());
        problem.setProperty("code", failure.code());
        RequestContext.requestId().ifPresent(id -> problem.setProperty("requestId", id));
        return problem;
    }
}
