package io.crowds.proxy.transport.proxy.ssh;

import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.NetAddr;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.TP;
import io.crowds.proxy.transport.Destination;
import io.crowds.proxy.transport.Transport;
import io.crowds.proxy.transport.proxy.AbstractProxyTransport;
import io.crowds.proxy.transport.proxy.ssh.sshd.ChannelDelegateService;
import io.crowds.proxy.transport.proxy.ssh.sshd.ChannelDelegateServiceFactoryFactory;
import io.crowds.util.Async;
import io.crowds.util.Lambdas;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.auth.AuthenticationIdentitiesProvider;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.keyverifier.*;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.AttributeRepository;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.config.keys.loader.KeyPairResourceLoader;
import org.apache.sshd.common.future.KeyExchangeFuture;
import org.apache.sshd.common.future.SshFuture;
import org.apache.sshd.common.future.SshFutureListener;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.common.util.io.resource.PathResource;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class SshProxyTransport extends AbstractProxyTransport {
    private final static Logger logger= LoggerFactory.getLogger(SshProxyTransport.class);


    private final SshClient sshClient;
    private SshOption sshOption;
    private final ChannelDelegateServiceFactoryFactory channelDelegateServiceFactoryFactory;

    public SshProxyTransport(ChannelCreator channelCreator,SshOption sshOption) {
        super(channelCreator,sshOption);
        this.sshOption = sshOption;
        this.channelDelegateServiceFactoryFactory=new ChannelDelegateServiceFactoryFactory();
        this.sshClient = setupClient();
    }

    protected Iterable<KeyPair> readKeyPairs(SessionContext session, Path keyPath, OpenOption... options)
            throws IOException, GeneralSecurityException {
        PathResource location = new PathResource(keyPath, options);
        try (InputStream inputStream = location.openInputStream()) {
            return doReadKeyPairs(session, location, inputStream);
        }
    }

    protected Iterable<KeyPair> doReadKeyPairs(SessionContext session, NamedResource resourceKey, InputStream inputStream)
            throws IOException, GeneralSecurityException {
        return SecurityUtils.loadKeyPairIdentities(session, resourceKey, inputStream, null);
    }
    private SshClient setupClient(){
        var sshClient = SshClient.setUpDefaultClient();
        ServerKeyVerifier verifier = switch (sshOption.getVerifyStrategy()){
            case REJECT -> RejectAllServerKeyVerifier.INSTANCE;
            case REJECT_UNKNOWNS -> new DefaultKnownHostsServerKeyVerifier(RejectAllServerKeyVerifier.INSTANCE);
            case ACCEPT_ONCE -> new DefaultKnownHostsServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
            case ACCEPT_ALL -> AcceptAllServerKeyVerifier.INSTANCE;
            case SPECIFIED -> {
                Path keyPath = sshOption.getServerKey();
                if (keyPath==null){
                    throw new IllegalArgumentException("Server Key is null");
                }
                PathResource location = new PathResource(keyPath);
                try (InputStream inputStream = location.openInputStream()) {
                    Iterable<KeyPair> keyPairs = SecurityUtils.loadKeyPairIdentities(null, location, inputStream, null);
                    for (KeyPair keyPair : keyPairs) {
                        if (keyPair.getPublic()!=null){
                            yield  new RequiredServerKeyVerifier(keyPair.getPublic());
                        }
                    }
                    throw new RuntimeException("Load Server Key failed");
                } catch (GeneralSecurityException | IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        sshClient.setServerKeyVerifier(verifier);

        List<Object> identities = new ArrayList<>();
        if (sshOption.getPassword()!=null){
            identities.add(sshOption.getPassword());
        }
        if (sshOption.getPrivateKey()!=null){
            Path keyPath = sshOption.getPrivateKey();
            String passphrase = sshOption.getPassphrase();
            try {
                KeyPairResourceLoader loader = SecurityUtils.getKeyPairResourceParser();
                Collection<KeyPair> keys = loader.loadKeyPairs(null, keyPath,FilePasswordProvider.of(passphrase));
                if (keys==null||keys.isEmpty()){
                    throw new RuntimeException("failed to load private key");
                }
                identities.addAll(keys);
            } catch (GeneralSecurityException | IOException e) {
                throw new RuntimeException("failed to load private key");
            }
        }
        if (!identities.isEmpty()) {
            sshClient.setPasswordIdentityProvider(AuthenticationIdentitiesProvider.wrapIdentities(identities));
        }
        sshClient.setIoServiceFactoryFactory(this.channelDelegateServiceFactoryFactory);
        sshClient.start();
        return sshClient;
    }

    @Override
    public String getTag() {
        return sshOption.getName();
    }

    @Override
    protected Destination getRemote(TP tp) {
        return new Destination(NetAddr.of(sshOption.getAddress()),TP.TCP);
    }

    private <S extends SshFuture<S>,T> void cascadeSshFuture(Lambdas.Supplier_WithExceptions<SshFuture<S>,IOException> futureSupplier,
                                                                           Promise<T> promise, SshFutureListener<S> listener){
        try {
            var future = futureSupplier.get();
            future.addListener(f->{
                if (f instanceof ConnectFuture connectFuture){
                    if (!connectFuture.isConnected()){
                        promise.tryFailure(connectFuture.getException());
                        return;
                    }
                }else if (f instanceof AuthFuture authFuture){
                    if (!authFuture.isSuccess()){
                        promise.tryFailure(authFuture.getException());
                        return;
                    }
                }else if (f instanceof KeyExchangeFuture keyExchangeFuture){
                    if (!keyExchangeFuture.isDone()){
                        promise.tryFailure(new IllegalStateException("Should not happen. keyExchangeFuture is not done yet. but trigger futureListener"));
                        return;
                    }
                    if (keyExchangeFuture.getException()!=null){
                        promise.tryFailure(keyExchangeFuture.getException());
                        return;
                    }
                }
                listener.operationComplete(f);
            });
        } catch (IOException e) {
            promise.tryFailure(e);
        }
    }

    @Override
    protected Future<Channel> proxy(Channel channel, NetLocation netLocation,Transport delegate) {
        Promise<Channel> promise = channel.eventLoop().newPromise();
        //here the channel already created. so we specify host to  127.0.0,1 to avoid dns lookup.
        cascadeSshFuture(
                ()->sshClient.connect(sshOption.getUser(),"127.0.0.1",sshOption.getAddress().getPort(),
                        AttributeRepository.ofAttributesMap(Map.of(ChannelDelegateService.CHANNEL,channel))),
                promise,
                future->{
                    ClientSession clientSession = future.getClientSession();
                    cascadeSshFuture(clientSession::auth,promise, _->{
                        SshSession session = new SshSession(channel, clientSession);
                        Async.cascadeFailure(
                                session.start(),
                                promise,
                                _->session.allocTunnel(netLocation.getDst())
                                          .addListener(Async.cascade(promise))
                        );
                    });
                });

        return promise;
    }

    @Override
    public Future<Channel> createChannel(EventLoop eventLoop, NetLocation netLocation, Transport transport) throws Exception {
        if (netLocation.getTp()== TP.UDP){
            return eventLoop.newFailedFuture(new IllegalArgumentException("SSH protocol does not support proxy UDP"));
        }
        return super.createChannel(eventLoop, netLocation,transport);
    }
}
