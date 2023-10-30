package io.crowds.proxy.routing;

import io.crowds.proxy.DomainNetAddr;
import io.crowds.proxy.NetAddr;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.routing.rule.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class AbstractRouter implements Router{
    private final static Logger logger= LoggerFactory.getLogger(AbstractRouter.class);
    protected String defaultTag;
    private Rule setDefaultTag(String tag){
        if (tag!=null)
            this.defaultTag=tag;

        return null;
    }

    public String getDefaultTag() {
        return defaultTag;
    }

    protected void initRule(List<String> ruleStr, Consumer<Rule> ruleHandler){
        ruleStr.stream()
                .filter(Objects::nonNull)
                .filter(Predicate.not(String::isBlank))
                .forEach(str->{
                    Rule rule = Rule.of(str);
                    if (rule!=null){
                        if (rule.type()==RuleType.DEFAULT){
                            setDefaultTag(rule.getTag());
                        }else{
                            ruleHandler.accept(rule);
                        }
                    }else{
                        logger.error("unrecognized rule: {}",str);
                    }

                });
    }


    protected abstract String routing(NetLocation netLocation, RuleType... types);

    public String routing(NetLocation netLocation){
        if (netLocation.getDst() instanceof DomainNetAddr){
            return routing(netLocation, RuleType.EQ,RuleType.EW,RuleType.KW,RuleType.DOMAIN,RuleType.PORT,RuleType.SRC_CIDR,RuleType.SRC_POST);
        }else {
            return routing(netLocation, RuleType.CIDR,RuleType.PORT,RuleType.SRC_CIDR,RuleType.SRC_POST,RuleType.GEOIP);
        }
    }

    public String routing(InetSocketAddress src, String domain){
        var net=new NetLocation(new NetAddr(src),new DomainNetAddr(domain,0), null);
        return routing(net,RuleType.SRC_CIDR,RuleType.SRC_POST,RuleType.EQ,RuleType.EW,RuleType.KW,RuleType.DOMAIN);
    }

    public String routingIp(InetAddress address, boolean dest){
        NetAddr netAddr = new NetAddr(new InetSocketAddress(address, 0));
        var net=new NetLocation(dest?null:netAddr, dest?netAddr:null, null);
        if (dest){
            return routing(net, RuleType.CIDR,RuleType.GEOIP);
        }else {
            return routing(net, RuleType.SRC_CIDR);
        }
    }

    public String routing(InetSocketAddress address,boolean dest){
        NetAddr netAddr = NetAddr.of(address);
        var net=new NetLocation(dest?null:netAddr, dest?netAddr:null, null);
        if (dest){
            return routing(net, RuleType.CIDR, RuleType.PORT,RuleType.GEOIP);
        }else{
            return routing(net, RuleType.SRC_CIDR, RuleType.SRC_POST);
        }

    }
}
