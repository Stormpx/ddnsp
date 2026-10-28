package io.crowds.proxy.transport.ws;

import io.netty.handler.codec.http.HttpHeaders;

public class WsOption {
    public static final int DEFAULT_MAX_FRAME_PAYLOAD_LENGTH = 65536;

    private String path="/";
    private HttpHeaders headers;
    private String secProtocol;
    private int maxFramePayloadLength = DEFAULT_MAX_FRAME_PAYLOAD_LENGTH;

    public WsOption() {
    }

    public WsOption(WsOption other) {
        this.path = other.path;
        this.headers = other.headers==null?null:other.headers.copy();
        this.secProtocol = other.secProtocol;
        this.maxFramePayloadLength = other.maxFramePayloadLength;
    }

    public String getPath() {
        return path;
    }

    public WsOption setPath(String path) {
        this.path = path;
        return this;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public WsOption setHeaders(HttpHeaders headers) {
        this.headers = headers;
        return this;
    }

    /**
     * the value of the {@code Sec-WebSocket-Protocol} header which is sent during the websocket handshake.
     * null means the header will not be sent, a blank string means the same thing.
     */
    public String getSecProtocol() {
        return secProtocol;
    }

    public WsOption setSecProtocol(String secProtocol) {
        this.secProtocol = secProtocol;
        return this;
    }

    public int getMaxFramePayloadLength() {
        return maxFramePayloadLength;
    }

    public WsOption setMaxFramePayloadLength(int maxFramePayloadLength) {
        this.maxFramePayloadLength = maxFramePayloadLength;
        return this;
    }
}
