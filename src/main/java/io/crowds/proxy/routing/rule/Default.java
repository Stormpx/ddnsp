package io.crowds.proxy.routing.rule;

import io.crowds.proxy.NetLocation;

public class Default implements Rule{
    private final String tag;

    public Default(String tag) {
        this.tag = tag;
    }

    @Override
    public RuleType type() {
        return RuleType.DEFAULT;
    }

    @Override
    public String content() {
        return type().getRule()+";;"+tag;
    }

    @Override
    public boolean match(NetLocation netLocation) {
        return false;
    }

    @Override
    public String getTag() {
        return tag;
    }
}
