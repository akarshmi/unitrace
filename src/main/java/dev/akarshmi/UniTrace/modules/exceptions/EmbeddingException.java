package dev.akarshmi.UniTrace.modules.exceptions;

/**
 * Failure while producing an embedding. {@link #isTransientFailure()} tells the job whether a retry can help
 * (timeouts, 5xx, 429, malformed output) or not (bad input, 4xx, configuration/dimension mismatch).
 */
public class EmbeddingException extends RuntimeException {

    private final boolean transientFailure;

    private EmbeddingException(String message, boolean transientFailure, Throwable cause) {
        super(message, cause);
        this.transientFailure = transientFailure;
    }

    public static EmbeddingException transientError(String message, Throwable cause) {
        return new EmbeddingException(message, true, cause);
    }

    public static EmbeddingException permanent(String message) {
        return new EmbeddingException(message, false, null);
    }

    public static EmbeddingException permanent(String message, Throwable cause) {
        return new EmbeddingException(message, false, cause);
    }

    public boolean isTransientFailure() {
        return transientFailure;
    }
}
