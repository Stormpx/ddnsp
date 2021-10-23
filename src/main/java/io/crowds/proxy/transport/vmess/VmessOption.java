package io.crowds.proxy.transport.vmess;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.UUID;

public class VmessOption {
    private InetSocketAddress address;
    private Security security;
    private User user;
    private String netWork;
    private boolean tls;
    private WsOption ws;
    private int connIdle=300;


    public InetSocketAddress getAddress() {
        return address;
    }

    public VmessOption setAddress(InetSocketAddress address) {
        this.address = address;
        return this;
    }

    public Security getSecurity() {
        return security;
    }

    public VmessOption setSecurity(Security security) {
        this.security = security;
        return this;
    }

    public User getUser() {
        return user;
    }

    public VmessOption setUser(User user) {
        this.user = user;
        return this;
    }

    public String getNetWork() {
        return netWork;
    }

    public VmessOption setNetWork(String netWork) {
        this.netWork = netWork;
        return this;
    }

    public boolean isTls() {
        return tls;
    }

    public VmessOption setTls(boolean tls) {
        this.tls = tls;
        return this;
    }

    public WsOption getWs() {
        return ws;
    }

    public VmessOption setWs(WsOption ws) {
        this.ws = ws;
        return this;
    }

    public int getConnIdle() {
        return connIdle;
    }

    public VmessOption setConnIdle(int connIdle) {
        this.connIdle = connIdle;
        return this;
    }

    public static class WsOption{
        private String path;
        private HttpHeaders headers;


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
}
