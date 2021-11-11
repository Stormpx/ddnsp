package io.crowds.proxy.routing.rule;

import io.crowds.proxy.DomainNetAddr;
import io.crowds.proxy.NetLocation;

public class KeyWord implements Rule {

    private String keyword;
    private String tag;

    public KeyWord(String keyword, String tag) {
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
        if (netLocation.getDest() instanceof DomainNetAddr){
            return netLocation.getDest().getHost().contains(keyword);
        }
        return false;
    }

    @Override
    public String getTag() {
        return tag;
    }

}
