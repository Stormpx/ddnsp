package io.crowds.proxy.common;

public class HandlerName {

    private String base;

    public HandlerName(String base) {
        this.base = base;
    }

    public String base() {
        return base;
    }

    public String with(String name){
        return base+"-"+name;
    }
}
