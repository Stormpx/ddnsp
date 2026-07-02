package io.crowds.api;

import io.vertx.core.json.JsonObject;

public class Responses {

    private int code;
    private Object data;
    private String message;

    public static Responses ok(Object data) {
        Responses r = new Responses();
        r.code = 0;
        r.data = data;
        return r;
    }

    public static Responses fail(int code, String message) {
        Responses r = new Responses();
        r.code = code;
        r.message = message;
        return r;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject().put("code", code);
        if (code == 0) {
            json.put("data", data);
        } else {
            json.put("message", message);
        }
        return json;
    }
}
