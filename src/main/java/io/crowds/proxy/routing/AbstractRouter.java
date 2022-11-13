package io.crowds.proxy.routing;

import io.crowds.proxy.DomainNetAddr;
import io.crowds.proxy.NetAddr;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.routing.rule.*;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class AbstractRouter implements Router{
    protected String defaultTag;
    private Rule setDefaultTag(String tag){
        if (tag!=null)
            this.defaultTag=tag;

        return null;
    }

    protected Rule lookupRule(int seq,String type, String content, String tag){
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

    protected void initRule(List<String> ruleStr, Consumer<Rule> ruleHandler){
        var ref = new Object() {
            int counter = 0;
        };
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
                    Rule rule = lookupRule(ref.counter++,ruleType, content.trim(), tag.trim());
                    if (rule!=null)
                        ruleHandler.accept(rule);

                });
    }



    protected abstract String routingIp(NetLocation netLocation, RuleType... types);

    public String routing(NetLocation netLocation){
        if (netLocation.getDest() instanceof DomainNetAddr){
            return routingIp(netLocation, RuleType.EQ,RuleType.EW,RuleType.KW,RuleType.DOMAIN,RuleType.PORT,RuleType.SRC_CIDR,RuleType.SRC_POST);
        }else {
            return routingIp(netLocation, RuleType.CIDR,RuleType.PORT,RuleType.SRC_CIDR,RuleType.SRC_POST,RuleType.GEOIP);
        }
    }

    public String routing(InetSocketAddress src, String domain){
        var net=new NetLocation(new NetAddr(src),new DomainNetAddr(domain,0), null);
        return routingIp(net,RuleType.SRC_CIDR,RuleType.SRC_POST,RuleType.EQ,RuleType.EW,RuleType.KW,RuleType.DOMAIN);
    }

    public String routingIp(InetAddress address, boolean dest){
        NetAddr netAddr = new NetAddr(new InetSocketAddress(address, 0));
        var net=new NetLocation(dest?null:netAddr, dest?netAddr:null, null);
        if (dest){
            return routingIp(net, RuleType.CIDR,RuleType.GEOIP);
        }else {
            return routingIp(net, RuleType.SRC_CIDR);
        }
    }

    public String routing(InetSocketAddress address,boolean dest){
        NetAddr netAddr = NetAddr.of(address);
        var net=new NetLocation(dest?null:netAddr, dest?netAddr:null, null);
        if (dest){
            return routingIp(net, RuleType.CIDR, RuleType.PORT,RuleType.GEOIP);
        }else{
            return routingIp(net, RuleType.SRC_CIDR, RuleType.SRC_POST);
        }

    }
}
