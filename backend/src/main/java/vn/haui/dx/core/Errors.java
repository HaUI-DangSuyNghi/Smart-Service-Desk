package vn.haui.dx.core;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.common.ErrorCode;
import vn.haui.dx.core.common.Problems;

@RestControllerAdvice
public class Errors {
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflict() { return Problems.of(ErrorCode.CONFLICT, "Resource already exists or violates a constraint"); }

    @ExceptionHandler(CoreException.class)
    public ProblemDetail core(CoreException error) { return Problems.of(error.code(), error.getMessage()); }
}
