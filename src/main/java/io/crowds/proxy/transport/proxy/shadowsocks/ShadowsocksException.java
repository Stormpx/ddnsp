package io.crowds.proxy.transport.proxy.shadowsocks;

import io.netty.handler.codec.DecoderException;

public class ShadowsocksException extends DecoderException {
    public ShadowsocksException() {
    }

    public ShadowsocksException(String message, Throwable cause) {
        super(message, cause);
    }

    public ShadowsocksException(String message) {
        super(message);
    }

    public ShadowsocksException(Throwable cause) {
        super(cause);
    }
}
