package io.crowds.proxy.transport.vmess;

import io.crowds.proxy.transport.ProtocolOption;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.UUID;

public class VmessOption extends ProtocolOption {
    private InetSocketAddress address;
    private Security security;
    private User user;
    private String netWork;
    private boolean tls;
    private boolean tlsAllowInsecure=false;
    private String tlsServerName;
    private WsOption ws;



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


    public boolean isTlsAllowInsecure() {
        return tlsAllowInsecure;
    }

    public VmessOption setTlsAllowInsecure(boolean tlsAllowInsecure) {
        this.tlsAllowInsecure = tlsAllowInsecure;
        return this;
    }

    public String getTlsServerName() {
        return tlsServerName;
    }

    public VmessOption setTlsServerName(String tlsServerName) {
        this.tlsServerName = tlsServerName;
        return this;
    }

    public static class WsOption{
        private String path="/";
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
