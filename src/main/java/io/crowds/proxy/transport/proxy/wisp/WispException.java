package io.crowds.proxy.transport.proxy.wisp;

public class WispException extends RuntimeException {

    public WispException(String message) {
        super(message);
    }

    public WispException(String message, Throwable cause) {
        super(message, cause);
    }
}
