package io.crowds.proxy.routing;

import io.crowds.proxy.DomainNetAddr;
import io.crowds.proxy.NetAddr;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.routing.rule.*;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.function.Predicate;

public class LinearRouter extends AbstractRouter{

    private List<Rule> rules;



    public LinearRouter(List<String> rules){
        this.rules=new ArrayList<>();
        initRule(rules,it->this.rules.add(it));
    }

    protected String routing(NetLocation netLocation, RuleType... types){
        Set<RuleType> typeSet=Set.of(types);

        for (Rule rule : this.rules) {
            if (typeSet.contains(rule.type())&&rule.match(netLocation)){
                return rule.getTag();
            }
        }
        return defaultTag;
    }



    public List<Rule> getRules(){
        return this.rules;
    }



}
