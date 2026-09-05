package dev.akarshmi.UniTrace.modules.exceptions;

import dev.akarshmi.UniTrace.modules.auth.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleException(
            HttpMessageNotReadableException exception
    ) {
        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse(
                        "Invalid or missing request body.",
                        "error",
                        HttpServletResponse.SC_BAD_REQUEST,
                        Instant.now().toString(),
                        "/"
                ));
    }


}
