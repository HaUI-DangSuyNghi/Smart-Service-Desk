package vn.haui.dx.core;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import static org.springframework.http.HttpStatus.CONFLICT;

@RestControllerAdvice
public class Errors {
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflict() { return ProblemDetail.forStatusAndDetail(CONFLICT, "Resource already exists or violates a constraint"); }
}
