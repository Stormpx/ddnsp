package io.crowds.api;

import io.crowds.proxy.routing.RoutingManager;
import io.crowds.proxy.routing.RoutingManager.OverrideRule;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.impl.RouterImpl;

import java.util.List;

public class RouteRuleService extends RouterImpl {

    private final RoutingManager routingManager;

    public RouteRuleService(Vertx vertx, RoutingManager routingManager) {
        super(vertx);
        this.routingManager = routingManager;

        get("/rules/override").handler(this::listOverride);
        post("/rules/override").handler(this::addOverride);
        delete("/rules/override/:id").handler(this::removeOverride);

        get("/rules/disabled").handler(this::listDisabled);
        post("/rules/disable").handler(this::addDisabled);
        delete("/rules/disable").handler(this::removeDisabled);

        get("/rules/all").handler(this::listAll);
    }

    // -- Override --

    private void listOverride(RoutingContext ctx) {
        List<OverrideRule> rules = routingManager.getOverrideRules();
        JsonArray arr = new JsonArray();
        for (OverrideRule r : rules) {
            arr.add(new JsonObject().put("id", r.id()).put("rule", r.rule()));
        }
        ctx.json(Responses.ok(arr).toJson());
    }

    private void addOverride(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String rule = body.getString("rule");
        if (rule == null || rule.isBlank()) {
            ctx.json(Responses.fail(400, "missing field: rule").toJson());
            return;
        }
        try {
            String id = routingManager.addOverrideRule(rule);
            ctx.json(Responses.ok(new JsonObject().put("id", id).put("rule", rule)).toJson());
        } catch (IllegalArgumentException e) {
            ctx.json(Responses.fail(400, e.getMessage()).toJson());
        }
    }

    private void removeOverride(RoutingContext ctx) {
        String id = ctx.pathParam("id");
        if (routingManager.removeOverrideRule(id)) {
            ctx.json(Responses.ok(null).toJson());
        } else {
            ctx.json(Responses.fail(404, "override rule not found: " + id).toJson());
        }
    }

    // -- Disabled --

    private void listDisabled(RoutingContext ctx) {
        JsonArray arr = new JsonArray();
        for (String r : routingManager.getDisabledRules()) {
            arr.add(r);
        }
        ctx.json(Responses.ok(arr).toJson());
    }

    private void addDisabled(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String rule = body.getString("rule");
        if (rule == null || rule.isBlank()) {
            ctx.json(Responses.fail(400, "missing field: rule").toJson());
            return;
        }
        if (routingManager.addDisabledRule(rule)) {
            ctx.json(Responses.ok(null).toJson());
        } else {
            ctx.json(Responses.fail(400, "rule not found in base rules: " + rule).toJson());
        }
    }

    private void removeDisabled(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String rule = body.getString("rule");
        if (rule == null || rule.isBlank()) {
            ctx.json(Responses.fail(400, "missing field: rule").toJson());
            return;
        }
        if (routingManager.removeDisabledRule(rule)) {
            ctx.json(Responses.ok(null).toJson());
        } else {
            ctx.json(Responses.fail(400, "rule is not disabled: " + rule).toJson());
        }
    }

    // -- All --

    private void listAll(RoutingContext ctx) {
        JsonArray overrideArr = new JsonArray();
        for (OverrideRule r : routingManager.getOverrideRules()) {
            overrideArr.add(new JsonObject().put("id", r.id()).put("rule", r.rule()));
        }
        JsonArray baseArr = new JsonArray();
        for (String r : routingManager.getBaseRules()) {
            baseArr.add(r);
        }
        JsonArray disabledArr = new JsonArray();
        for (String r : routingManager.getDisabledRules()) {
            disabledArr.add(r);
        }
        ctx.json(Responses.ok(new JsonObject()
                .put("override", overrideArr)
                .put("base", baseArr)
                .put("disabled", disabledArr)).toJson());
    }

}
