package dev.akarshmi.UniTrace.modules.exceptions.customExceptions;

public class InvalidRegistrationTokenException extends RuntimeException {
    public InvalidRegistrationTokenException(String message) {
        super(message);
    }
}
