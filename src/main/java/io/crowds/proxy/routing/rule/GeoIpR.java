package io.crowds.proxy.routing.rule;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.NetLocation;
import io.crowds.util.Mmdb;

import java.util.Objects;

public class GeoIpR implements Rule{

    private boolean not=false;
    private final String isoCode;
    private final String tag;

    public GeoIpR(String isoCode, String tag) {
        Objects.requireNonNull(isoCode);
        this.tag = tag;
        if (isoCode.startsWith("!")){
            not=true;
            isoCode=isoCode.substring(1);
        }
        this.isoCode = isoCode;
    }

    @Override
    public RuleType type() {
        return RuleType.GEOIP;
    }

    @Override
    public String content() {
        return isoCode;
    }

    @Override
    public boolean match(NetLocation netLocation) {
        NetAddr addr = netLocation.getDest();
        if (addr.isIpv4()||addr.isIpv6()){
            String code = Mmdb.instance().queryIsoCode(addr.getAsInetAddr().getAddress());
            return not != this.isoCode.equalsIgnoreCase(code);
//            if (code!=null){
//            }
        }
        return false;
    }

    @Override
    public String getTag() {
        return tag;
    }
}
