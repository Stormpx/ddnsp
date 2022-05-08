package io.crowds.proxy.select;


public enum Method {

    RR("rr"),
    WRR("wrr"),
    HASH("hash"),
    RAND("rand"),
    ROUTING("routing"),
    ;

    private String value;

    Method(String value) {
        this.value = value;
    }


    public static Method of(String s){
        if (s==null)
            return null;
        Method[] values = Method.values();
        for (int i = 0; i < values.length; i++) {
            if (values[i].value.equalsIgnoreCase(s)){
                return values[i];
            }
        }
        return null;
    }
}
