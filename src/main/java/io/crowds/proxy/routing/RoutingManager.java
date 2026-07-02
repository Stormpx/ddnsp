package io.crowds.proxy.routing;

import io.crowds.proxy.NetLocation;
import io.crowds.proxy.routing.rule.Rule;
import io.crowds.proxy.routing.rule.RuleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class RoutingManager implements Router {
    private static final Logger logger = LoggerFactory.getLogger(RoutingManager.class);

    private final CopyOnWriteArrayList<OverrideRule> overrideRules = new CopyOnWriteArrayList<>();
    private final Set<String> disabledRules = ConcurrentHashMap.newKeySet();
    private volatile List<String> baseRules;
    private volatile Router activeRouter;

    public RoutingManager() {
        this(null);
    }

    public RoutingManager(List<String> baseRules) {
        this.baseRules = baseRules == null ? List.of() : List.copyOf(baseRules);
        rebuildRouter();
    }

    // -- Router interface --

    @Override
    public String routing(NetLocation netLocation) {
        Router router = this.activeRouter;
        return router != null ? router.routing(netLocation) : null;
    }

    @Override
    public String routing(InetSocketAddress src, String domain) {
        Router router = this.activeRouter;
        return router != null ? router.routing(src, domain) : null;
    }

    @Override
    public String routingIp(InetAddress address, boolean dest) {
        Router router = this.activeRouter;
        return router != null ? router.routingIp(address, dest) : null;
    }

    @Override
    public String routing(InetSocketAddress address, boolean dest) {
        Router router = this.activeRouter;
        return router != null ? router.routing(address, dest) : null;
    }

    // -- Override rules --

    public String addOverrideRule(String ruleStr) {
        Rule rule = Rule.of(ruleStr);
        if (rule == null) {
            throw new IllegalArgumentException("invalid rule: " + ruleStr);
        }
        String id = UUID.randomUUID().toString().substring(0, 8);
        overrideRules.add(new OverrideRule(id, ruleStr));
        rebuildRouter();
        return id;
    }

    public boolean removeOverrideRule(String id) {
        boolean removed = overrideRules.removeIf(r -> r.id.equals(id));
        if (removed) {
            rebuildRouter();
        }
        return removed;
    }

    public List<OverrideRule> getOverrideRules() {
        return List.copyOf(overrideRules);
    }

    // -- Disabled rules --

    public boolean addDisabledRule(String ruleFingerprint) {
        if (!baseRules.contains(ruleFingerprint)) {
            return false;
        }
        boolean added = disabledRules.add(ruleFingerprint);
        if (added) {
            rebuildRouter();
        }
        return added;
    }

    public boolean removeDisabledRule(String ruleFingerprint) {
        boolean removed = disabledRules.remove(ruleFingerprint);
        if (removed) {
            rebuildRouter();
        }
        return removed;
    }

    public Set<String> getDisabledRules() {
        return Set.copyOf(disabledRules);
    }

    // -- Base rules --

    public void updateBaseRules(List<String> newBaseRules) {
        this.baseRules = newBaseRules == null ? List.of() : List.copyOf(newBaseRules);
        rebuildRouter();
    }

    public List<String> getBaseRules() {
        return baseRules;
    }

    // -- Internal --

    private void rebuildRouter() {
        List<String> allRules = Stream.concat(overrideRules.stream().map(OverrideRule::rule),
                                              baseRules.stream().filter(Predicate.not(disabledRules::contains)))
                                      .toList();

        this.activeRouter = allRules.isEmpty() ? null : new CachedRouter(allRules, 2, 12, 12);
    }

    public record OverrideRule(String id, String rule) {}
}
