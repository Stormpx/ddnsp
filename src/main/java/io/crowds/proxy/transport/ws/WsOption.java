package io.crowds.proxy.transport.ws;

import io.netty.handler.codec.http.HttpHeaders;

public class WsOption {
    private String path="/";
    private HttpHeaders headers;

    public WsOption() {
    }

    public WsOption(WsOption other) {
        this.path = other.path;
        this.headers = other.headers==null?null:other.headers.copy();
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
}
