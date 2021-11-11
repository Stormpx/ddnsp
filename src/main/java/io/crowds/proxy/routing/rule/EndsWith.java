package io.crowds.proxy.routing.rule;

import io.crowds.proxy.DomainNetAddr;
import io.crowds.proxy.NetLocation;


public class EndsWith implements Rule{

    private String suffix;
    private String tag;

    public EndsWith(String suffix, String tag) {
        this.suffix = suffix;
        this.tag = tag;
    }

    @Override
    public boolean match(NetLocation netLocation) {
        if (netLocation.getDest() instanceof DomainNetAddr){
            return netLocation.getDest().getHost().endsWith(suffix);
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
