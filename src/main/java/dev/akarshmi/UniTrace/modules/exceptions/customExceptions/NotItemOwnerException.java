package dev.akarshmi.UniTrace.modules.exceptions.customExceptions;

public class NotItemOwnerException extends RuntimeException {

    public NotItemOwnerException(String message) {
        super(message);
    }
    public NotItemOwnerException() {
        super("You are not allowed to perform this action");
    }
}
