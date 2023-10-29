package io.crowds.util;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.*;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

import java.util.function.Consumer;

public class Async {


    public static <T> io.vertx.core.Future<T> toFuture(Future<T> future){
        io.vertx.core.Promise<T> promise = io.vertx.core.Promise.promise();
        future.addListener((FutureListener<T>)f->{
            if (f.isCancelled()){
                promise.tryFail("callback cancelled");
            }else if (!f.isSuccess()){
                promise.tryFail(f.cause());
            }else{
                promise.tryComplete(f.get());
            }
        });
        return promise.future();
    }

    public static <T> Handler<AsyncResult<T>> futureCascadeCallback(Promise<T> promise) {
        return ar->{
            if (ar.succeeded()){
                promise.trySuccess(ar.result());
            }else{
                promise.tryFailure(ar.cause());
            }
        };
    }


    //-------------------------------------------------------------------------callback below----------------------------------------------------------------------------------------

    public static <T> Future<T> toCallback(EventLoop eventLoop, io.vertx.core.Future<T> future){
        Promise<T> promise = eventLoop.newPromise();
        future.onComplete(ar->{
            if (ar.succeeded()){
                promise.trySuccess(ar.result());
            }else{
                promise.tryFailure(ar.cause());
            }
        });
        return promise;
    }



    public static <V,F extends Future<V>> void cascadeFailure(F future, Promise<?> promise, FutureListener<V> futureListener){
        future.addListener((FutureListener<V>) f->{
            if (f.isCancelled()){
                if (promise.isCancellable()){
                    promise.cancel(false);
                }
            } else if (!f.isSuccess()) {
                promise.tryFailure(f.cause());
            }else{
                futureListener.operationComplete(f);
            }
        });
    }

    public static <V,F extends Future<V>> void cascadeFailure0(F future, Promise<?> promise, GenericFutureListener<F> futureListener){
        future.addListener((GenericFutureListener<F>) f->{
            if (f.isCancelled()){
                if (promise.isCancellable()){
                    promise.cancel(false);
                }
            } else if (!f.isSuccess()) {
                promise.tryFailure(f.cause());
            }else{
                futureListener.operationComplete(f);
            }
        });
    }

    public static <V> FutureListener<V> cascade(Promise<V> promise){
        return f-> {
            if (f.isCancelled()){
                if (promise.isCancellable()){
                    promise.cancel(false);
                }
            } else if (!f.isSuccess()) {
                promise.tryFailure(f.cause());
            }else{
                promise.trySuccess(f.get());
            }
        };
    }

}
