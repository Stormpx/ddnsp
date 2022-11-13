package io.crowds.proxy.select;

import io.crowds.proxy.ProxyContext;
import io.crowds.proxy.routing.LinearRouter;
import io.crowds.proxy.routing.rule.Rule;

import java.util.List;
import java.util.stream.Collectors;

public class Routing extends TransportSelector{

    private LinearRouter router;

    public Routing(String name,List<String> rules) {
        super(name);
        this.router=new LinearRouter(rules);
    }

    @Override
    public List<String> tags() {
        return router.getRules()
                .stream()
                .map(Rule::getTag)
                .collect(Collectors.toList());
    }

    @Override
    public String nextTag(ProxyContext proxyContext) {
        return router.routing(proxyContext.getNetLocation());
    }
}
