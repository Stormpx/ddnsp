package io.crowds;

import io.crowds.dns.ClientOption;
import io.crowds.dns.DnsClient;
import io.crowds.dns.InternalDnsResolver;
import io.netty.channel.EventLoopGroup;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.DefaultNameResolver;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.AddressResolver;
import io.vertx.core.impl.VertxImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class Ddnsp {
    private final static Logger logger= LoggerFactory.getLogger(Ddnsp.class);

    public final static Vertx VERTX;
    private final static AtomicReference<InternalDnsResolver> INTERNAL_DNS_RESOLVER =new AtomicReference<>();

    static {
        VERTX =Vertx.vertx(new VertxOptions()
                .setBlockedThreadCheckInterval(5000)
                .setWorkerPoolSize(Runtime.getRuntime().availableProcessors()/2)
                .setInternalBlockingPoolSize(Runtime.getRuntime().availableProcessors())
                .setPreferNativeTransport(true));

    }

//    static {
//        try {
//            var varhandle = fetchUnsafeHandler().findVarHandle(AddressResolver.class,"resolverGroup",AddressResolverGroup.class);
//            if (VERTX instanceof VertxImpl impl){
//                AddressResolver resolver = impl.addressResolver();
//                varhandle.set(resolver,new DdnspAddressResolverGroup());
//            }
//        } catch (Exception e) {
//            logger.error("hook vertx AddressResolver failed",e);
//        }
//    }
//
//    private static Unsafe getUnsafe() {
//        Class<Unsafe> aClass = Unsafe.class;
//        try {
//            Field unsafe = aClass.getDeclaredField("theUnsafe");
//            unsafe.setAccessible(true);
//            return ((Unsafe) unsafe.get(null));
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//
//    }
//
//    private static MethodHandles.Lookup fetchUnsafeHandler() {
//        Class<MethodHandles.Lookup> lookupClass = MethodHandles.Lookup.class;
//        try {
//            Field implLookupField = lookupClass.getDeclaredField("IMPL_LOOKUP");
//            implLookupField.setAccessible(true);
//            var unsafe = getUnsafe();
//            long offset = unsafe.staticFieldOffset(implLookupField);
//            return (MethodHandles.Lookup) unsafe.getObject(unsafe.staticFieldBase(implLookupField), offset);
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }

    public static EventLoopGroup acceptor(){
        return ((VertxImpl)VERTX).getAcceptorEventLoopGroup();
    }

    public static void initDnsResolver(InternalDnsResolver dnsClient){
        INTERNAL_DNS_RESOLVER.set(dnsClient);
    }

    public static InternalDnsResolver dnsResolver(){
        InternalDnsResolver client = INTERNAL_DNS_RESOLVER.get();
        if (client==null){
            INTERNAL_DNS_RESOLVER.compareAndSet(null,new DnsClient(VERTX,new ClientOption()));
            client=INTERNAL_DNS_RESOLVER.get();
        }
        return client;
    }

    private static class DdnspAddressResolverGroup extends AddressResolverGroup<InetSocketAddress> {
        @Override
        protected io.netty.resolver.AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) throws Exception {
            return new DdnspNameResolver(executor).asAddressResolver();
        }
    }

    private static class DdnspNameResolver extends DefaultNameResolver {

        public DdnspNameResolver(EventExecutor executor) {
            super(executor);
        }

        @Override
        protected void doResolve(String inetHost, Promise<InetAddress> promise) throws Exception {
            dnsResolver().resolve(inetHost,null)
                    .onComplete(ar->{
                        if (ar.succeeded()){
                            promise.trySuccess(ar.result());
                        }else{
                            promise.tryFailure(ar.cause());
                        }
                    });
        }

        @Override
        protected void doResolveAll(String inetHost, Promise<List<InetAddress>> promise) throws Exception {
            InternalDnsResolver resolver = dnsResolver();
            var future = switch (resolver){
                case DnsClient dnsClient->dnsClient.requestAll(inetHost,false);
                default -> resolver.resolve(inetHost,null).map(List::of);
            };
            future.onComplete(ar->{
                if (ar.succeeded()){
                    promise.trySuccess(ar.result());
                }else{
                    promise.tryFailure(ar.cause());
                }
            });
        }
    }

}
