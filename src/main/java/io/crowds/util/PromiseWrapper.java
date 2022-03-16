package io.crowds.util;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

public class PromiseWrapper<T> {

    private Promise<T> promise;

    public PromiseWrapper(Promise<T> promise) {
        this.promise = promise;
    }

    public Future<T> future(){
        return promise;
    }




}
