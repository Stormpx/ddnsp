package io.crowds;

import io.crowds.compoments.dns.InternalDnsResolver;
import io.crowds.compoments.dns.FallbackDnsResolver;
import io.netty.resolver.AddressResolverGroup;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.resolver.NameResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stormpx.net.PartialNetStack;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class Ddnsp {
    private final static Logger logger= LoggerFactory.getLogger(Ddnsp.class);

    private final static AtomicReference<InternalDnsResolver> INTERNAL_DNS_RESOLVER =new AtomicReference<>();


    private static MethodHandles.Lookup fetchMethodHandlesLookup() {
        Class<MethodHandles.Lookup> lookupClass = MethodHandles.Lookup.class;
        try {
            Field implLookupField = lookupClass.getDeclaredField("IMPL_LOOKUP");
            implLookupField.setAccessible(true);
            return (MethodHandles.Lookup) implLookupField.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Context newContext(Supplier<InternalDnsResolver> resolverSupplier){
        var netStack = new PartialNetStack();
        var vertx =Vertx.builder()
                    .withTransport(new DdnspTransport(netStack))
                    .with(new VertxOptions()
                            .setBlockedThreadCheckInterval(5000)
                            .setWorkerPoolSize(Math.max(Runtime.getRuntime().availableProcessors()/2,1))
                            .setInternalBlockingPoolSize(Runtime.getRuntime().availableProcessors())
                    )
                    .build();
        var context = new Context((VertxInternal) vertx,netStack,resolverSupplier);
        try {
            var varhandle = fetchMethodHandlesLookup().findVarHandle(NameResolver.class,"resolverGroup",AddressResolverGroup.class);
            if (vertx instanceof VertxInternal impl){
                NameResolver resolver = impl.nameResolver();
                varhandle.set(resolver,context.getNettyResolver());
            }
        } catch (Exception e) {
            logger.error("hook vertx AddressResolver failed. {}",e.getMessage());
        }
        return context;
    }

    public static void initDnsResolver(InternalDnsResolver dnsClient){
        INTERNAL_DNS_RESOLVER.set(dnsClient);
    }

    public static InternalDnsResolver dnsResolver(){
        InternalDnsResolver client = INTERNAL_DNS_RESOLVER.get();
        if (client==null){
            INTERNAL_DNS_RESOLVER.compareAndSet(null,new FallbackDnsResolver());
            client=INTERNAL_DNS_RESOLVER.get();
        }
        return client;
    }



}
