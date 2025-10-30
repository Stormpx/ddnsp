package io.crowds.proxy.services.xdp;

public class XdpServerOption {
    private boolean enable;
    private String iface;
    private String mac;
    private String address;
    private String gateway;
    private Integer mtu;
    private XdpOpt opt;

    public Integer getMtu() {
        return mtu;
    }

    public XdpServerOption setMtu(Integer mtu) {
        this.mtu = mtu;
        return this;
    }

    public boolean isEnable() {
        return enable;
    }

    public XdpServerOption setEnable(boolean enable) {
        this.enable = enable;
        return this;
    }

    public String getIface() {
        return iface;
    }

    public XdpServerOption setIface(String iface) {
        this.iface = iface;
        return this;
    }

    public String getMac() {
        return mac;
    }

    public XdpServerOption setMac(String mac) {
        this.mac = mac;
        return this;
    }

    public String getAddress() {
        return address;
    }

    public XdpServerOption setAddress(String address) {
        this.address = address;
        return this;
    }

    public String getGateway() {
        return gateway;
    }

    public XdpServerOption setGateway(String gateway) {
        this.gateway = gateway;
        return this;
    }

    public XdpOpt getOpt() {
        return opt;
    }

    public XdpServerOption setOpt(XdpOpt opt) {
        this.opt = opt;
        return this;
    }
}
