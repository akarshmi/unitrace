package dev.akarshmi.UniTrace.modules.exceptions.customExceptions;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
    public InvalidRefreshTokenException() { super("Invalid or malformed refresh token."); }

}
