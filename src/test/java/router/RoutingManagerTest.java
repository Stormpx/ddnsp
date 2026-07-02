package router;

import io.crowds.proxy.routing.Router;
import io.crowds.proxy.routing.RoutingManager;
import io.crowds.proxy.routing.RoutingManager.OverrideRule;
import io.netty.util.NetUtil;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RoutingManagerTest extends RouterTest {

    @Override
    protected Router setupRouter(List<String> rules) {
        return new RoutingManager(rules);
    }

    @Test
    public void testOverridePriority() throws UnknownHostException {
        var baseRules = List.of(
                "domain;google.com;base",
                "cidr;170.0.0.0/8;base"
        );
        RoutingManager manager = new RoutingManager(baseRules);
        InetSocketAddress src = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 1);

        // Base rules work normally
        Assert.assertEquals("base", manager.routing(src, "www.google.com"));
        Assert.assertEquals("base", manager.routingIp(NetUtil.createInetAddressFromIpAddressString("170.1.2.3"), true));

        // Add override rule that shadows the base domain rule
        String id = manager.addOverrideRule("domain;google.com;override");
        Assert.assertEquals("override", manager.routing(src, "www.google.com"));
        // Non-overlapping base rule still works
        Assert.assertEquals("base", manager.routingIp(NetUtil.createInetAddressFromIpAddressString("170.1.2.3"), true));

        // Remove override, base rule takes over again
        Assert.assertTrue(manager.removeOverrideRule(id));
        Assert.assertEquals("base", manager.routing(src, "www.google.com"));
    }

    @Test
    public void testDisableBaseRule() throws UnknownHostException {
        var baseRules = List.of(
                "domain;google.com;tag1",
                "domain;example.com;tag2"
        );
        RoutingManager manager = new RoutingManager(baseRules);
        InetSocketAddress src = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 1);

        Assert.assertEquals("tag1", manager.routing(src, "www.google.com"));
        Assert.assertEquals("tag2", manager.routing(src, "www.example.com"));

        // Disable one rule
        Assert.assertTrue(manager.addDisabledRule("domain;google.com;tag1"));
        Assert.assertNull(manager.routing(src, "www.google.com"));
        Assert.assertEquals("tag2", manager.routing(src, "www.example.com"));

        // Re-enable
        Assert.assertTrue(manager.removeDisabledRule("domain;google.com;tag1"));
        Assert.assertEquals("tag1", manager.routing(src, "www.google.com"));
    }

    @Test
    public void testDisableNonexistentRule() {
        var baseRules = List.of("domain;google.com;tag1");
        RoutingManager manager = new RoutingManager(baseRules);

        // Cannot disable a rule not in base
        Assert.assertFalse(manager.addDisabledRule("domain;example.com;tag2"));
        // Original rule still works
        Assert.assertEquals(Set.of(), manager.getDisabledRules());
    }

    @Test
    public void testOverrideAndDisableCombined() throws UnknownHostException {
        var baseRules = List.of(
                "domain;google.com;base",
                "domain;example.com;base"
        );
        RoutingManager manager = new RoutingManager(baseRules);
        InetSocketAddress src = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 1);

        // Disable base google rule, add override for google
        manager.addDisabledRule("domain;google.com;base");
        manager.addOverrideRule("domain;google.com;override");

        // Override wins even though base is disabled
        Assert.assertEquals("override", manager.routing(src, "www.google.com"));
        // Other base rule unaffected
        Assert.assertEquals("base", manager.routing(src, "www.example.com"));
    }

    @Test
    public void testUpdateBaseRulesPreservesState() throws UnknownHostException {
        var baseRules = List.of(
                "domain;google.com;tag1",
                "domain;example.com;tag2"
        );
        RoutingManager manager = new RoutingManager(baseRules);
        InetSocketAddress src = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 1);

        // Add override and disable
        String overrideId = manager.addOverrideRule("domain;youtube.com;override");
        manager.addDisabledRule("domain;google.com;tag1");

        // Hot-reload base rules (new rules added, old ones changed)
        var newBaseRules = List.of(
                "domain;google.com;tag1",
                "domain;example.com;tag2",
                "domain;github.com;tag3"
        );
        manager.updateBaseRules(newBaseRules);

        // Override preserved
        Assert.assertEquals("override", manager.routing(src, "www.youtube.com"));
        // Disabled rule still disabled
        Assert.assertNull(manager.routing(src, "www.google.com"));
        // New base rule works
        Assert.assertEquals("tag3", manager.routing(src, "www.github.com"));
        // Existing base rule works
        Assert.assertEquals("tag2", manager.routing(src, "www.example.com"));
    }

    @Test
    public void testUpdateBaseRulesCleansStaleDisabled() throws UnknownHostException {
        var baseRules = List.of(
                "domain;google.com;tag1",
                "domain;example.com;tag2"
        );
        RoutingManager manager = new RoutingManager(baseRules);
        InetSocketAddress src = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 1);

        manager.addDisabledRule("domain;google.com;tag1");
        Assert.assertEquals(Set.of("domain;google.com;tag1"), manager.getDisabledRules());

        // Remove google rule from base
        manager.updateBaseRules(List.of("domain;example.com;tag2"));

        // Remaining rule works
        Assert.assertEquals("tag2", manager.routing(src, "www.example.com"));
    }

    @Test
    public void testGetMethods() {
        var baseRules = List.of("domain;google.com;tag1", "domain;example.com;tag2");
        RoutingManager manager = new RoutingManager(baseRules);

        Assert.assertEquals(baseRules, manager.getBaseRules());
        Assert.assertEquals(List.of(), manager.getOverrideRules());
        Assert.assertEquals(Set.of(), manager.getDisabledRules());

        String id = manager.addOverrideRule("domain;youtube.com;override");
        manager.addDisabledRule("domain;google.com;tag1");

        List<OverrideRule> overrides = manager.getOverrideRules();
        Assert.assertEquals(1, overrides.size());
        Assert.assertEquals(id, overrides.get(0).id());
        Assert.assertEquals("domain;youtube.com;override", overrides.get(0).rule());

        Assert.assertEquals(Set.of("domain;google.com;tag1"), manager.getDisabledRules());
    }

    @Test
    public void testRemoveNonexistentOverride() {
        var baseRules = List.of("domain;google.com;tag1");
        RoutingManager manager = new RoutingManager(baseRules);
        Assert.assertFalse(manager.removeOverrideRule("nonexistent"));
    }

    @Test
    public void testRemoveNonDisabledRule() {
        var baseRules = List.of("domain;google.com;tag1");
        RoutingManager manager = new RoutingManager(baseRules);
        Assert.assertFalse(manager.removeDisabledRule("domain;google.com;tag1"));
    }
}
