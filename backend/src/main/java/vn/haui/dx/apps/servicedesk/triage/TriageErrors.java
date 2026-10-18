package vn.haui.dx.apps.servicedesk.triage;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.haui.dx.core.common.RequestContext;

@RestControllerAdvice(assignableTypes = TicketTriageController.class)
public class TriageErrors {
    @ExceptionHandler(TriageFailure.class)
    public ProblemDetail failure(TriageFailure failure) {
        var problem = ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatusCode.valueOf(failure.status()), failure.getMessage());
        problem.setProperty("code", failure.code());
        RequestContext.requestId().ifPresent(id -> problem.setProperty("requestId", id));
        return problem;
    }
}
