package io.crowds.proxy.routing.rule;

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

    private String rule;
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
}
