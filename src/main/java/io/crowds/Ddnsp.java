package io.crowds;

import io.crowds.compoments.dns.DdnspAddressResolverGroup;
import io.crowds.dns.ClientOption;
import io.crowds.dns.DnsClient;
import io.crowds.compoments.dns.InternalDnsResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.DefaultNameResolver;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.resolver.NameResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stormpx.net.PartialNetStack;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class Ddnsp {
    private final static Logger logger= LoggerFactory.getLogger(Ddnsp.class);

    private final static PartialNetStack NETSTACK;
    private final static Vertx VERTX;
    private final static Context CONTEXT;
    private final static AtomicReference<InternalDnsResolver> INTERNAL_DNS_RESOLVER =new AtomicReference<>();
    static {
        NETSTACK = new PartialNetStack();
        VERTX =Vertx.builder()
                    .withTransport(new DdnspTransport(NETSTACK))
                    .with(new VertxOptions()
                            .setBlockedThreadCheckInterval(5000)
                            .setWorkerPoolSize(Math.max(Runtime.getRuntime().availableProcessors()/2,1))
                            .setInternalBlockingPoolSize(Runtime.getRuntime().availableProcessors())
                    )
                    .build();
        CONTEXT = new Context((VertxInternal) VERTX,NETSTACK,new DdnspAddressResolverGroup(Ddnsp::dnsResolver));
    }

    static {
        try {
            var varhandle = fetchMethodHandlesLookup().findVarHandle(NameResolver.class,"resolverGroup",AddressResolverGroup.class);
            if (VERTX instanceof VertxInternal impl){
                NameResolver resolver = impl.nameResolver();
                varhandle.set(resolver,CONTEXT.getNettyResolver());
            }
        } catch (Exception e) {
            logger.error("hook vertx AddressResolver failed. {}",e.getMessage());
        }
    }


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

    public static PartialNetStack netStack(){
        return NETSTACK;
    }

    public static Vertx vertx(){
        return VERTX;
    }

    public static Context context(){
        return CONTEXT;
    }

    public static void initDnsResolver(InternalDnsResolver dnsClient){
        INTERNAL_DNS_RESOLVER.set(dnsClient);
    }

    public static InternalDnsResolver dnsResolver(){
        InternalDnsResolver client = INTERNAL_DNS_RESOLVER.get();
        if (client==null){
            INTERNAL_DNS_RESOLVER.compareAndSet(null,new DnsClient(context(),new ClientOption()));
            client=INTERNAL_DNS_RESOLVER.get();
        }
        return client;
    }



}
