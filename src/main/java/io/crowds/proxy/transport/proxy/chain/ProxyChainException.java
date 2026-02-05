package io.crowds.proxy.transport.proxy.chain;

public class ProxyChainException extends RuntimeException{
    public ProxyChainException() {
        super();
    }

    public ProxyChainException(String message) {
        super(message);
    }

    public ProxyChainException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProxyChainException(Throwable cause) {
        super(cause);
    }

    protected ProxyChainException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
