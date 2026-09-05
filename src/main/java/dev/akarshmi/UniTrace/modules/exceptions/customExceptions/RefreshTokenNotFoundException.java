package dev.akarshmi.UniTrace.modules.exceptions.customExceptions;

public class RefreshTokenNotFoundException extends RuntimeException {
    public RefreshTokenNotFoundException(String message) { super(message); }
    public RefreshTokenNotFoundException() { super("Refresh token not recognized."); }
}