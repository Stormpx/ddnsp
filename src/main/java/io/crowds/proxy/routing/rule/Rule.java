package io.crowds.proxy.routing.rule;

import io.crowds.proxy.NetLocation;

public interface Rule {

    RuleType type();

    String content();

    boolean match(NetLocation netLocation);

    String getTag();

    default String toStr(){
        return type().getRule()+";"+content()+";"+getTag();
    }

    private static Rule lookupRule(String type, String content, String tag){
        type=type.trim();
        RuleType ruleType = RuleType.of(type);
        if (ruleType==null){
            return null;
        }
        return switch (ruleType){
            case DOMAIN -> new Domain(content,tag);
            case EQ -> new Equal(content,tag);
            case EW -> new EndsWith(content,tag);
            case KW-> new KeyWord(content,tag);
            case SRC_CIDR-> new Cidr(content,tag,false);
            case CIDR-> new Cidr(content,tag,true);
            case SRC_POST-> new Port(content,tag,false);
            case PORT-> new Port(content,tag,true);
            case GEOIP -> new GeoIpR(content,tag);
            case DEFAULT -> new Default(tag);
        };
    }

    static Rule of(String str){
        int index = str.indexOf(';');
        int lastIndex = str.lastIndexOf(';');
        if (index==lastIndex){
            return null;
        }
        var ruleType=str.substring(0,index);
        var content=str.substring(index+1,lastIndex);
        var tag=str.substring(lastIndex+1);
        return lookupRule(ruleType,content,tag);
    }


}
