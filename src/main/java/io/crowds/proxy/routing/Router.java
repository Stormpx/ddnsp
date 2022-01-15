package io.crowds.proxy.routing;

import io.crowds.proxy.DomainNetAddr;
import io.crowds.proxy.NetAddr;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.TP;
import io.crowds.proxy.routing.rule.*;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.function.Predicate;

public class Router {

    private List<Rule> rules;


    public Router(List<String> ruleStr){
        initRule(ruleStr);
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
                    RuleType type = RuleType.of(ruleType.trim());
                    if (type==null){
                        return;
                    }
                    var content=str.substring(index+1,lastIndex);
                    var tag=str.substring(lastIndex+1);
                    Rule rule = createRule(type, content.trim(), tag.trim());
                    if (rule!=null)
                        rules.add(rule);

                });

        this.rules=rules;
    }

    private Rule createRule(RuleType ruleType,String content,String tag){
        return switch (ruleType){
            case DOMAIN -> new Domain(content,tag);
            case EQ -> new Equal(content,tag);
            case EW -> new EndsWith(content,tag);
            case KW-> new KeyWord(content,tag);
            case SRC_CIDR-> new Cidr(content,tag,false);
            case CIDR-> new Cidr(content,tag,true);
            case SRC_POST-> new Port(content,tag,false);
            case PORT-> new Port(content,tag,true);
        };

    }


    private String routing(NetLocation netLocation,RuleType... types){
        Set<RuleType> typeSet=new HashSet<>(Arrays.asList(types));

        for (Rule rule : this.rules) {
            if (typeSet.contains(rule.type())&&rule.match(netLocation)){
                return rule.getTag();
            }
        }
        return null;
    }

    public String routing(NetLocation netLocation){
        if (netLocation.getDest() instanceof DomainNetAddr){
            return routing(netLocation, RuleType.EQ,RuleType.EW,RuleType.KW,RuleType.DOMAIN,RuleType.PORT,RuleType.SRC_CIDR,RuleType.SRC_POST);
        }else {
            return routing(netLocation, RuleType.CIDR,RuleType.PORT,RuleType.SRC_CIDR,RuleType.SRC_POST);
        }
    }

    public String routing(InetSocketAddress src, String domain){
        var net=new NetLocation(new NetAddr(src),new DomainNetAddr(domain,0), null);
        return routing(net,RuleType.SRC_CIDR,RuleType.SRC_POST,RuleType.EQ,RuleType.EW,RuleType.KW,RuleType.DOMAIN);
    }

    public String routing(InetAddress address,boolean dest){
        NetAddr netAddr = new NetAddr(new InetSocketAddress(address, 0));
        var net=new NetLocation(dest?null:netAddr, dest?netAddr:null, null);
        return routing(net,dest?RuleType.CIDR:RuleType.SRC_CIDR);
    }

    public String routing(InetSocketAddress address,boolean dest){
        NetAddr netAddr = new NetAddr(address);
        var net=new NetLocation(dest?null:netAddr, dest?netAddr:null, null);
        return routing(net,dest?RuleType.CIDR:RuleType.SRC_CIDR,dest?RuleType.PORT:RuleType.SRC_POST);
    }



}
