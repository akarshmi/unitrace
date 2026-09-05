package dev.akarshmi.UniTrace.modules.exceptions.customExceptions;

public class ImageUploadException extends RuntimeException {
    public ImageUploadException(String message) {
        super(message);
    }
    public ImageUploadException() { super("Failed to upload image. Please try again."); }

}
