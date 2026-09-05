package dev.akarshmi.UniTrace.modules.exceptions.customExceptions;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OtpCooldownException extends RuntimeException {
    public OtpCooldownException(String message) {
        super(message);
    }
}
