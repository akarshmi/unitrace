package dev.akarshmi.UniTrace.modules.exceptions;

import java.time.Instant;
import java.util.stream.Collectors;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.akarshmi.UniTrace.modules.auth.dto.ErrorResponse;
import dev.akarshmi.UniTrace.modules.exceptions.customExceptions.InvalidHandoverKeyException;
import dev.akarshmi.UniTrace.modules.exceptions.customExceptions.InvalidMatchStateException;
import dev.akarshmi.UniTrace.modules.exceptions.customExceptions.ItemNotFoundException;
import dev.akarshmi.UniTrace.modules.exceptions.customExceptions.MatchNotFoundException;
import dev.akarshmi.UniTrace.modules.exceptions.customExceptions.NotItemOwnerException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Errors for the item / matching / desk modules. GlobalExceptionHandler is left untouched.
 * (MethodArgumentNotValidException extends BindException, so one handler covers JSON and multipart validation.)
 */
@RestControllerAdvice
public class MatchingExceptionHandler {

    @ExceptionHandler({MatchNotFoundException.class, ItemNotFoundException.class})
    public ResponseEntity<ErrorResponse> notFound(RuntimeException e, HttpServletRequest req) {
        return build(404, e.getMessage(), req);
    }

    @ExceptionHandler(NotItemOwnerException.class)
    public ResponseEntity<ErrorResponse> notOwner(NotItemOwnerException e, HttpServletRequest req) {
        return build(403, e.getMessage(), req);
    }

    @ExceptionHandler({InvalidMatchStateException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> conflict(Exception e, HttpServletRequest req) {
        String msg = e instanceof OptimisticLockingFailureException
                ? "This match was changed by someone else. Reload and try again." : e.getMessage();
        return build(409, msg, req);
    }

    @ExceptionHandler(InvalidHandoverKeyException.class)
    public ResponseEntity<ErrorResponse> badKey(InvalidHandoverKeyException e, HttpServletRequest req) {
        return build(422, e.getMessage(), req);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> validation(BindException e, HttpServletRequest req) {
        String msg = e.getBindingResult().getAllErrors().stream()
                .map(err -> err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(400, msg.isBlank() ? "Validation failed." : msg, req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> badRequest(IllegalArgumentException e, HttpServletRequest req) {
        return build(400, e.getMessage(), req);
    }

    private ResponseEntity<ErrorResponse> build(int status, String message, HttpServletRequest req) {
        return ResponseEntity.status(status).body(new ErrorResponse(
                message == null ? "Request failed." : message, "error", status,
                Instant.now().toString(), req.getRequestURI()));
    }
}
