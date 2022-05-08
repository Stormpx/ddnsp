package io.crowds.proxy.select;

import io.crowds.proxy.ProxyContext;
import io.crowds.proxy.routing.Router;
import io.crowds.proxy.routing.rule.Rule;

import java.util.List;
import java.util.stream.Collectors;

public class Routing extends TransportSelector{

    private Router router;

    public Routing(String name,List<String> rules) {
        super(name);
        this.router=new Router(rules);
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
