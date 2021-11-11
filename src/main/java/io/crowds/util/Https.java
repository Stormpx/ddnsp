package io.crowds.util;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientResponse;

public class Https {


    public static Future<Buffer> assertSuccess(HttpClientResponse resp){
        if (resp.statusCode()==200){
            return resp.body();
        }
        return Future.failedFuture("statuscode == "+resp.statusCode()+" "+resp.statusMessage());
    }

}
