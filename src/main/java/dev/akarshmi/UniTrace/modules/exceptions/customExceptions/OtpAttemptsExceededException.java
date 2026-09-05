package dev.akarshmi.UniTrace.modules.exceptions.customExceptions;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OtpAttemptsExceededException extends RuntimeException {
    public OtpAttemptsExceededException(String message) {
        super(message);
    }
}
