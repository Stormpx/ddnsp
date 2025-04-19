package io.crowds.proxy.routing.rule;

import io.crowds.proxy.NetLocation;

public enum  RuleType {
    DOMAIN("domain"),
    EQ("eq"),
    EW("ew"),
    KW("kw"),
    SRC_CIDR("src-cidr"),
    CIDR("cidr"),
    SRC_POST("src-port"),
    PORT("port"),
    GEOIP("geoip"),
    DEFAULT("default"),
    ;

    private final String rule;
    RuleType(String s) {
        this.rule=s;
    }

    public String getRule() {
        return rule;
    }

    public static RuleType of(String s){
        RuleType[] values = RuleType.values();
        for (int i = 0; i < values.length; i++) {
            if (values[i].rule.equalsIgnoreCase(s)){
                return values[i];
            }
        }
        return null;
    }

    public Object getMatchKey(NetLocation netLocation){
        return switch (this){
            case DOMAIN,EQ,EW,KW, GEOIP, CIDR -> netLocation.getDst().getHost();
            case SRC_CIDR -> netLocation.getSrc().getHost();
            case SRC_POST -> netLocation.getSrc().getPort();
            case PORT -> netLocation.getDst().getPort();
            case DEFAULT -> null;
        };
    }
}
