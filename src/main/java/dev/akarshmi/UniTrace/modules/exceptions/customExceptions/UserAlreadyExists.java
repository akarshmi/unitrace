package dev.akarshmi.UniTrace.modules.exceptions.customExceptions;

public class UserAlreadyExists extends RuntimeException {
    public UserAlreadyExists(String message) {
        super(message);
    }
}
