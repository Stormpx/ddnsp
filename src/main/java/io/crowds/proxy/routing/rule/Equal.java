package io.crowds.proxy.routing.rule;

import io.crowds.proxy.DomainNetAddr;
import io.crowds.proxy.NetLocation;

import java.util.Objects;

public class Equal implements Rule {

    private String target;
    private String tag;

    public Equal(String target, String tag) {
        this.target = target;
        this.tag = tag;
    }

    @Override
    public boolean match(NetLocation netLocation) {
        if (netLocation.getDst() instanceof DomainNetAddr){
            return Objects.equals(target,netLocation.getDst().getHost());
        }
        return false;
    }

    @Override
    public String getTag() {
        return tag;
    }


    @Override
    public RuleType type() {
        return RuleType.EQ;
    }

    @Override
    public String content() {
        return target;
    }

}
