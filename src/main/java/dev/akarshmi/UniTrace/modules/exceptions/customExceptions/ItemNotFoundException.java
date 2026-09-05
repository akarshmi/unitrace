package dev.akarshmi.UniTrace.modules.exceptions.customExceptions;

public class ItemNotFoundException extends RuntimeException {

    public ItemNotFoundException(String message) {
        super(message);
    }
    public ItemNotFoundException() {
        super("Item not found");
    }
}
