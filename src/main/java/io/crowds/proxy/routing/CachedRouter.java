package io.crowds.proxy.routing;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.routing.rule.Rule;
import io.crowds.proxy.routing.rule.RuleType;

import java.util.*;

public class CachedRouter extends AbstractRouter {
    private Map<RuleType,Slot> slotMap;


    public CachedRouter(List<String> rules,int k,int missSize,int hitSize) {
        this.slotMap=new HashMap<>();
        var ref = new Object(){
            int counter=0;
        };
        initRule(rules,it->this.slotMap.computeIfAbsent(it.type(),type->new Slot(type,missSize,hitSize)).addRule(new SequencedRule(ref.counter++,it)));
    }


    @Override
    protected String routing(NetLocation netLocation, RuleType... types) {
        Rule result = null;
        int seq = Integer.MAX_VALUE;
        for (RuleType type : types) {
            Slot slot = this.slotMap.get(type);
            if (slot == null || slot.minimalSeq > seq) {
                continue;
            }
            SequencedRule rule = slot.match(netLocation);
            if (rule!=null && rule.seq()<seq){
                result = rule.rule();
                seq = rule.seq();
            }
        }

        return result != null ? result.getTag() : defaultTag;
    }

    record SequencedRule(int seq,Rule rule){}

    static class Slot{
        private static final Object NULL=new Object();
        private final RuleType type;
        private final List<SequencedRule> rules;
        private final Cache<Object,Object> missCache;
        private final Cache<Object,SequencedRule> hitCache;
        private int minimalSeq = Integer.MAX_VALUE;

        public Slot(RuleType type,int missSize,int hitSize) {
            this.type = type;
            this.rules=new ArrayList<>();
            this.missCache = Caffeine.newBuilder().maximumSize(missSize).build();
            this.hitCache = Caffeine.newBuilder().maximumSize(hitSize).build();
        }

        public void addRule(SequencedRule rule){
            this.rules.add(rule);
            if (rule.seq()<minimalSeq){
                this.minimalSeq = rule.seq();
            }
        }

        public SequencedRule match(NetLocation netLocation){
            Object key = type.getMatchKey(netLocation);
            if (key==null)
                return null;

            if (missCache.getIfPresent(key) != null)
                return null;

            var hit = hitCache.getIfPresent(key);
            if (hit!=null){
                return hit;
            }
            for (SequencedRule sequencedRule : rules) {
                if (sequencedRule.rule().match(netLocation)){
                    hitCache.put(key,sequencedRule);
                    return sequencedRule;
                }
            }
            missCache.put(key,NULL);
            return null;
        }

    }

}
