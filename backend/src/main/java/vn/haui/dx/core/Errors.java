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
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ProblemDetail mediaType() { return Problems.of(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Unsupported request media type"); }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ProblemDetail uploadSize() { return Problems.of(ErrorCode.PAYLOAD_TOO_LARGE, "File exceeds upload limit"); }

    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class,
        org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    public ProblemDetail validation() { return Problems.of(ErrorCode.VALIDATION_FAILED, "Invalid request"); }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflict() { return Problems.of(ErrorCode.CONFLICT, "Resource already exists or violates a constraint"); }

    @ExceptionHandler(CoreException.class)
    public ProblemDetail core(CoreException error) { return Problems.of(error.code(), error.getMessage()); }
}
