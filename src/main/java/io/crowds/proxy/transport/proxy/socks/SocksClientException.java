package io.crowds.proxy.transport.proxy.socks;

public class SocksClientException extends RuntimeException{

    public SocksClientException() {
        super();
    }

    public SocksClientException(String message) {
        super(message);
    }

    public SocksClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public SocksClientException(Throwable cause) {
        super(cause);
    }

    protected SocksClientException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
