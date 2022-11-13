package io.crowds.proxy.routing.rule;

import io.crowds.proxy.NetLocation;

public interface Rule {

    RuleType type();

    String content();

    boolean match(NetLocation netLocation);

    String getTag();





}
