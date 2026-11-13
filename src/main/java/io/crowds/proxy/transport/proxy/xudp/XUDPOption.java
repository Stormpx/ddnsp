package io.crowds.proxy.transport.proxy.xudp;

public class XUDPOption {

    private boolean enable = true;
    private boolean proxy443 = false;

    public boolean isEnable() {
        return enable;
    }

    public XUDPOption setEnable(boolean enable) {
        this.enable = enable;
        return this;
    }

    public boolean isProxy443() {
        return proxy443;
    }

    public XUDPOption setProxy443(boolean proxy443) {
        this.proxy443 = proxy443;
        return this;
    }
}
