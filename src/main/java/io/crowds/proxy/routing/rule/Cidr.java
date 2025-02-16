package io.crowds.proxy.routing.rule;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.NetLocation;
import io.crowds.util.IPCIDR;

public class Cidr implements Rule {
    private final boolean dest;
    private final IPCIDR ipcidr;
    private final String tag;

    public Cidr(String ipcidr, String tag,boolean dest) {
        this.ipcidr = new IPCIDR(ipcidr);
        this.tag = tag;
        this.dest=dest;
    }


    @Override
    public String content() {
        return ipcidr.toString();
    }

    @Override
    public boolean match(NetLocation netLocation) {
        NetAddr addr = this.dest?netLocation.getDst():netLocation.getSrc();
        if (addr.isIpv4()|| addr.isIpv6()){
            return ipcidr.isMatch(addr.getByte());
        }
        return false;
    }

    @Override
    public String getTag() {
        return tag;
    }

    @Override
    public RuleType type() {
        return dest?RuleType.CIDR:RuleType.SRC_CIDR;
    }

}
