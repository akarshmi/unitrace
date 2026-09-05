package dev.akarshmi.UniTrace.modules.exceptions.customExceptions;

public class RegistrationInProgressException extends RuntimeException {
    public RegistrationInProgressException(String message) {
        super(message);
    }
}
