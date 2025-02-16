package io.crowds.proxy.routing.rule;

import io.crowds.proxy.DomainNetAddr;
import io.crowds.proxy.NetLocation;

import java.util.Objects;

public class Domain implements Rule {
    private final String domain;
    private final String tag;

    public Domain(String domain, String tag) {
        Objects.requireNonNull(domain,"domain");
        this.domain = domain.toLowerCase();
        this.tag = tag;
    }

    @Override
    public RuleType type() {
        return RuleType.DOMAIN;
    }

    @Override
    public String content() {
        return domain;
    }

    @Override
    public boolean match(NetLocation netLocation) {
        if(netLocation.getDst() instanceof DomainNetAddr){
            String host = netLocation.getDst().getHost();
            if (!host.regionMatches(true, host.length() - domain.length(), domain, 0, domain.length())){
                return false;
            }
            return host.length()==domain.length()||host.charAt(host.length()-domain.length()-1)=='.';
        }
        return false;
    }

    @Override
    public String getTag() {
        return tag;
    }
}
