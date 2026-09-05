package dev.akarshmi.UniTrace.modules.exceptions.customExceptions;

public class RefreshTokenExpiredException extends RuntimeException {
    public RefreshTokenExpiredException(String message) { super(message); }
    public RefreshTokenExpiredException() { super("Refresh token has expired."); }
}