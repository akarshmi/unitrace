package dev.akarshmi.UniTrace.modules.exceptions.customExceptions;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OtpLockedException extends RuntimeException {
    public OtpLockedException(String message) {
        super(message);
    }
}
