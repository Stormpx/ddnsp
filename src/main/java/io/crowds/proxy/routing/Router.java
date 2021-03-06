package io.crowds.proxy.routing;

import io.crowds.proxy.DomainNetAddr;
import io.crowds.proxy.NetAddr;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.routing.rule.*;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.function.Predicate;

public class Router {

    private List<Rule> rules;

    private String defaultTag;

    public Router(List<String> rules){
        initRule(rules);
    }


    private Rule setDefaultTag(String tag){
        if (tag!=null)
            this.defaultTag=tag;

        return null;
    }

    private void initRule(List<String> ruleStr){
        List<Rule> rules=new ArrayList<>();

        ruleStr.stream()
                .filter(Objects::nonNull)
                .filter(Predicate.not(String::isBlank))
                .forEach(str->{
                    int index = str.indexOf(';');
                    int lastIndex = str.lastIndexOf(';');
                    if (index==lastIndex){
                        return;
                    }
                    var ruleType=str.substring(0,index);
                    var content=str.substring(index+1,lastIndex);
                    var tag=str.substring(lastIndex+1);
                    Rule rule = lookupRule(ruleType, content.trim(), tag.trim());
                    if (rule!=null)
                        rules.add(rule);

                });

        this.rules=rules;
    }

    private Rule lookupRule(String type, String content, String tag){
        type=type.trim();
        RuleType ruleType = RuleType.of(type);
        if (ruleType==null){
            return null;
        }
        var r=switch (ruleType){
            case DOMAIN -> new Domain(content,tag);
            case EQ -> new Equal(content,tag);
            case EW -> new EndsWith(content,tag);
            case KW-> new KeyWord(content,tag);
            case SRC_CIDR-> new Cidr(content,tag,false);
            case CIDR-> new Cidr(content,tag,true);
            case SRC_POST-> new Port(content,tag,false);
            case PORT-> new Port(content,tag,true);
            case GEOIP -> new GeoIpR(content,tag);
            case DEFAULT -> setDefaultTag(tag);
        };
        return r;
    }


    private String routing(NetLocation netLocation,RuleType... types){
        Set<RuleType> typeSet=new HashSet<>(Arrays.asList(types));

        for (Rule rule : this.rules) {
            if (typeSet.contains(rule.type())&&rule.match(netLocation)){
                return rule.getTag();
            }
        }
        return defaultTag;
    }

    public String routing(NetLocation netLocation){
        if (netLocation.getDest() instanceof DomainNetAddr){
            return routing(netLocation, RuleType.EQ,RuleType.EW,RuleType.KW,RuleType.DOMAIN,RuleType.PORT,RuleType.SRC_CIDR,RuleType.SRC_POST);
        }else {
            return routing(netLocation, RuleType.CIDR,RuleType.PORT,RuleType.SRC_CIDR,RuleType.SRC_POST,RuleType.GEOIP);
        }
    }

    public String routing(InetSocketAddress src, String domain){
        var net=new NetLocation(new NetAddr(src),new DomainNetAddr(domain,0), null);
        return routing(net,RuleType.SRC_CIDR,RuleType.SRC_POST,RuleType.EQ,RuleType.EW,RuleType.KW,RuleType.DOMAIN);
    }

    public String routing(InetAddress address,boolean dest){
        NetAddr netAddr = new NetAddr(new InetSocketAddress(address, 0));
        var net=new NetLocation(dest?null:netAddr, dest?netAddr:null, null);
        if (dest){
            return routing(net, RuleType.CIDR,RuleType.GEOIP);
        }else {
            return routing(net, RuleType.SRC_CIDR);
        }
    }

    public String routing(InetSocketAddress address,boolean dest){
        NetAddr netAddr = new NetAddr(address);
        var net=new NetLocation(dest?null:netAddr, dest?netAddr:null, null);
        if (dest){
            return routing(net, RuleType.CIDR, RuleType.PORT,RuleType.GEOIP);
        }else{
            return routing(net, RuleType.SRC_CIDR, RuleType.SRC_POST);
        }

    }

    public List<Rule> getRules(){
        return this.rules;
    }



}
