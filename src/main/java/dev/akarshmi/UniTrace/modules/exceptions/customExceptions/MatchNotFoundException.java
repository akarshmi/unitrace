package dev.akarshmi.UniTrace.modules.exceptions.customExceptions;

public class MatchNotFoundException extends RuntimeException {

    public MatchNotFoundException() {
        super("Match not found.");
    }

    public MatchNotFoundException(String message) {
        super(message);
    }
}
