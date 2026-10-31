package dev.akarshmi.UniTrace.modules.exceptions.customExceptions;

public class InvalidHandoverKeyException extends RuntimeException {

    public InvalidHandoverKeyException() {
        super("Invalid handover key.");
    }

    public InvalidHandoverKeyException(String message) {
        super(message);
    }
}
