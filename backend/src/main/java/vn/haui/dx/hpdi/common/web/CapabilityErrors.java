package vn.haui.dx.hpdi.common.web;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;
import vn.haui.dx.core.common.ErrorCode;
import vn.haui.dx.core.common.Problems;
import vn.haui.dx.hpdi.common.api.CapabilityException;
@RestControllerAdvice
public class CapabilityErrors {
    @ExceptionHandler(CapabilityException.class)
    public ProblemDetail capability(CapabilityException error) {
        return Problems.of(ErrorCode.valueOf(error.code().name()), error.getMessage());
    }
}
