package dev.akarshmi.UniTrace.modules.exceptions.customExceptions;

public class InvalidMatchStateException extends RuntimeException {

    public InvalidMatchStateException() {
        super("Invalid match state.");
    }

    public InvalidMatchStateException(String message) {
        super(message);
    }
}
