package dev.akarshmi.UniTrace.modules.utils.ApiResponse;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data
) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(
                true,
                "Request successful",
                data
        );
    }

    public static <T> ApiResponse<T> of(String message, T data) {
        return new ApiResponse<>(
                true,
                message,
                data
        );
    }

    public static <T> ApiResponse<T> of(String message) {
        return new ApiResponse<>(
                true,
                message,
                null
        );
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(
                false,
                message,
                null
        );
    }
}