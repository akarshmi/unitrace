package dev.akarshmi.UniTrace.modules.exceptions.customExceptions;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OtpExpiredException extends RuntimeException {
    public OtpExpiredException(String message) {
        super(message);
    }
}
