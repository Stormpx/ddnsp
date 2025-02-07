package io.crowds;

import io.crowds.dns.ClientOption;
import io.crowds.dns.DnsClient;
import io.crowds.dns.InternalDnsResolver;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
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

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class Ddnsp {
    private final static Logger logger= LoggerFactory.getLogger(Ddnsp.class);

    private final static Vertx VERTX;
    private final static Context CONTEXT;
    private final static AtomicReference<InternalDnsResolver> INTERNAL_DNS_RESOLVER =new AtomicReference<>();

    static {
        VERTX =Vertx.vertx(new VertxOptions()
                .setBlockedThreadCheckInterval(5000)
                .setWorkerPoolSize(Math.max(Runtime.getRuntime().availableProcessors()/2,1))
                .setInternalBlockingPoolSize(Runtime.getRuntime().availableProcessors())
                .setPreferNativeTransport(true));

        CONTEXT = new Context((VertxImpl) VERTX);
    }

    static {
        try {
            var varhandle = fetchMethodHandlesLookup().findVarHandle(AddressResolver.class,"resolverGroup",AddressResolverGroup.class);
            if (VERTX instanceof VertxImpl impl){
                AddressResolver resolver = impl.addressResolver();
                varhandle.set(resolver,new DdnspAddressResolverGroup());
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
