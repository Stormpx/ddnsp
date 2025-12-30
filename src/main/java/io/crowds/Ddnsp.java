package io.crowds;

import io.crowds.compoments.dns.FallbackDnsResolver;
import io.crowds.compoments.dns.InternalDnsResolver;
import io.netty.resolver.AddressResolverGroup;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.resolver.NameResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stormpx.net.PartialNetStack;
import sun.reflect.ReflectionFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class Ddnsp {
    private static final Logger logger= LoggerFactory.getLogger(Ddnsp.class);
    private static final FallbackDnsResolver FALLBACK_DNS_RESOLVER = new FallbackDnsResolver();
    private static final StableValue<InternalDnsResolver> INTERNAL_DNS_RESOLVER =StableValue.of();
    private static MethodHandles.Lookup lookup=null;

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
    public static MethodHandles.Lookup fetchMethodHandlesLookup0() {
        if (lookup!=null){
            return lookup;
        }
        try {
            ReflectionFactory reflectionFactory = ReflectionFactory.getReflectionFactory();
            Class<MethodHandles.Lookup> lookupClass = MethodHandles.Lookup.class;
            Constructor<MethodHandles.Lookup> lookupConstructor = lookupClass.getDeclaredConstructor(Class.class, Class.class, int.class);
            Constructor<?> constructor = reflectionFactory.newConstructorForSerialization(lookupClass, lookupConstructor);
            lookup =  (MethodHandles.Lookup) constructor.newInstance(Object.class,null,-1);
            return lookup;
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
            var varhandle = fetchMethodHandlesLookup0().findVarHandle(NameResolver.class,"resolverGroup",AddressResolverGroup.class);
            VertxInternal impl = (VertxInternal) vertx;
            NameResolver resolver = impl.nameResolver();
            varhandle.set(resolver,context.getNettyResolver());
        } catch (Exception e) {
            logger.error("hook vertx AddressResolver failed. {}",e.getMessage());
        }
        return context;
    }

    public static void logStartTime(Duration cost){
        String unit = "Seconds";
        long ms = cost.toMillis();
        long granularity = TimeUnit.SECONDS.toMillis(1);
        double time = (double) ms /granularity;
        logger.info("Started ddnsp in {} {}",time,unit);
    }

    public static void initDnsResolver(InternalDnsResolver dnsClient){
        if (!INTERNAL_DNS_RESOLVER.trySet(dnsClient)){
            logger.warn("DnsResolver already initialized, ignore this set");
        }
    }

    public static InternalDnsResolver dnsResolver(){
        return INTERNAL_DNS_RESOLVER.orElse(FALLBACK_DNS_RESOLVER);
    }



}
