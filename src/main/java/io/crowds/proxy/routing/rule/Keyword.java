package io.crowds.proxy.routing.rule;

import io.crowds.proxy.DomainNetAddr;
import io.crowds.proxy.NetLocation;

public class Keyword implements Rule {

    private final String keyword;
    private final String tag;

    public Keyword(String keyword, String tag) {
        this.keyword = keyword;
        this.tag = tag;
    }


    @Override
    public RuleType type() {
        return RuleType.KW;
    }

    @Override
    public String content() {
        return keyword;
    }

    @Override
    public boolean match(NetLocation netLocation) {
        if (netLocation.getDst() instanceof DomainNetAddr){
            return netLocation.getDst().getHost().contains(keyword.toLowerCase());
        }
        return false;
    }

    @Override
    public String getTag() {
        return tag;
    }

}
