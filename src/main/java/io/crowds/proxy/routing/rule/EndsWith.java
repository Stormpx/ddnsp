package io.crowds.proxy.routing.rule;

import io.crowds.proxy.DomainNetAddr;
import io.crowds.proxy.NetLocation;


public class EndsWith implements Rule{

    private final String suffix;
    private final String tag;

    public EndsWith(String suffix, String tag) {
        this.suffix = suffix;
        this.tag = tag;
    }

    @Override
    public boolean match(NetLocation netLocation) {
        if (netLocation.getDst() instanceof DomainNetAddr){
            String host = netLocation.getDst().getHost();
            return host.regionMatches(true, host.length() - suffix.length(), suffix, 0, suffix.length());
        }
        return false;
    }

    @Override
    public String getTag() {
        return tag;
    }


    @Override
    public RuleType type() {
        return RuleType.EW;
    }

    @Override
    public String content() {
        return suffix;
    }

}
