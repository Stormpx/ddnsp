package io.crowds.proxy.routing;

import io.crowds.proxy.NetLocation;
import io.crowds.proxy.routing.rule.Rule;
import io.crowds.proxy.routing.rule.RuleType;
import io.crowds.util.LRUK;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class CachedRouter extends AbstractRouter {
    private Map<RuleType,Slot> slotMap;


    public CachedRouter(List<String> rules,int k,int missSize,int hitSize) {
        this.slotMap=new HashMap<>();
        var ref = new Object(){
            int counter=0;
        };
        initRule(rules,it->this.slotMap.computeIfAbsent(it.type(),type->new Slot(type,k,missSize,hitSize)).addRule(new SequencedRule(ref.counter++,it)));
    }


    @Override
    protected String routing(NetLocation netLocation, RuleType... types) {
        return Arrays.stream(types)
                .map(it -> this.slotMap.get(it))
                .filter(Objects::nonNull)
                .map(slot -> slot.match(netLocation))
                .filter(Objects::nonNull)
                .min(Comparator.comparing(SequencedRule::seq))
                .map(SequencedRule::rule)
                .map(Rule::getTag)
                .orElse(defaultTag);

    }

    record SequencedRule(int seq,Rule rule){}

    class Slot{
        private RuleType type;
        private List<SequencedRule> rules;
        private LRUK<Object,?> missCache;
        private LRUK<Object,SequencedRule> hitCache;

        public Slot(RuleType type,int k,int missSize,int hitSize) {
            this.type = type;
            this.rules=new ArrayList<>();
            this.missCache=new LRUK<>(k,missSize);
            this.hitCache=new LRUK<>(k,hitSize);
        }

        public void addRule(SequencedRule rule){
            this.rules.add(rule);
        }

        public SequencedRule match(NetLocation netLocation){
            Object key = type.getMatchKey(netLocation);
            if (missCache.exists(key))
                return null;
            if (hitCache.exists(key)){
                return hitCache.get(key);
            }
            for (SequencedRule sequencedRule : rules) {
                if (sequencedRule.rule().match(netLocation)){
                    hitCache.access(key,sequencedRule);
                    return sequencedRule;
                }
            }
            missCache.access(key,null);
            return null;
        }

    }

}
