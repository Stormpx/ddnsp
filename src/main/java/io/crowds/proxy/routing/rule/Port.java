package io.crowds.proxy.routing.rule;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.NetLocation;

import java.util.Objects;

public class Port implements Rule {
    private final boolean dest;
    private final Integer port;
    private final String tag;

    public Port(String port, String tag,boolean dest) {
        this.port=Integer.valueOf(port);
        if (this.port>65535||this.port<0){
            throw new IllegalArgumentException("invalid port "+port);
        }
        this.dest = dest;
        this.tag = tag;
    }


    @Override
    public RuleType type() {
        return dest?RuleType.PORT:RuleType.SRC_POST;
    }

    @Override
    public String content() {
        return port.toString();
    }

    @Override
    public boolean match(NetLocation netLocation) {
        NetAddr dest = this.dest?netLocation.getDst():netLocation.getSrc();
        return Objects.equals(dest.getPort(),this.port);
    }

    @Override
    public String getTag() {
        return tag;
    }
}
