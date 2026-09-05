package dev.akarshmi.UniTrace.modules.exceptions.customExceptions;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PendingRegistrationExpiredException extends RuntimeException {
    public PendingRegistrationExpiredException(String message) {
        super(message);
    }
}
