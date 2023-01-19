package io.crowds.tun;

import io.crowds.util.IPCIDR;

public class TunOption {

    protected String name;

    private IPCIDR ipcidr;
    private Integer mtu;

    public String getName() {
        return name;
    }

    public TunOption setName(String name) {
        this.name = name;
        return this;
    }

    public Integer getMtu() {
        return mtu;
    }

    public TunOption setMtu(Integer mtu) {
        this.mtu = mtu;
        return this;
    }

    public IPCIDR getIpcidr() {
        return ipcidr;
    }

    public TunOption setIpcidr(IPCIDR ipcidr) {
        this.ipcidr = ipcidr;
        return this;
    }
}
