package dev.akarshmi.UniTrace.modules.exceptions.customExceptions;

public class RefreshTokenRevokedException extends RuntimeException {
    public RefreshTokenRevokedException(String message) {
        super(message);
    }
    public RefreshTokenRevokedException() { super("Refresh token has been revoked."); }
}
