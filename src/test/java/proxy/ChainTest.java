package proxy;

import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.ProxyTransport;
import io.crowds.proxy.transport.TlsOption;
import io.crowds.proxy.transport.TransportOption;
import io.crowds.proxy.transport.proxy.ProxyTransportProvider;
import io.crowds.proxy.transport.proxy.chain.ChainOption;
import io.crowds.proxy.transport.proxy.chain.ChainProxyTransport;
import io.crowds.proxy.transport.proxy.chain.NodeType;
import io.crowds.proxy.transport.proxy.shadowsocks.CipherAlgo;
import io.crowds.proxy.transport.proxy.shadowsocks.ShadowsocksOption;
import io.crowds.proxy.transport.proxy.shadowsocks.ShadowsocksTransport;
import io.crowds.proxy.transport.proxy.socks.SocksOption;
import io.crowds.proxy.transport.proxy.socks.SocksProxyTransport;
import io.crowds.proxy.transport.proxy.ssh.SshOption;
import io.crowds.proxy.transport.proxy.ssh.SshProxyTransport;
import io.crowds.proxy.transport.proxy.trojan.TrojanOption;
import io.crowds.proxy.transport.proxy.trojan.TrojanProxyTransport;
import io.crowds.proxy.transport.proxy.vless.VlessOption;
import io.crowds.proxy.transport.proxy.vless.VlessProxyTransport;
import io.crowds.proxy.transport.proxy.vmess.Security;
import io.crowds.proxy.transport.proxy.vmess.User;
import io.crowds.proxy.transport.proxy.vmess.VmessOption;
import io.crowds.proxy.transport.proxy.vmess.VmessProxyTransport;
import io.crowds.proxy.transport.ws.WsOption;
import io.crowds.util.Inet;
import io.crowds.util.Lambdas;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.Testcontainers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ChainTest extends ProxyTestBase {

    protected record ProxyServer(ProxyTransport header,ProxyTransport node){

    }

    private ProxyServer createServer(XrayRule xrayRule,ProtocolOption option,Function<ProtocolOption,ProxyTransport> createTransport) throws IOException {
        xrayRule.start(option);
        return new ProxyServer(
                createTransport.apply(xrayRule.getOutside()),
                createTransport.apply(xrayRule.getInside())
        );
    }

    @Rule
    public XrayRule vmessRule = new XrayRule();
    private ProxyServer createVmessProxy(ChannelCreator channelCreator,String name) throws IOException {
        InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 16823);
        var option=new VmessOption()
                .setAddress(dest)
                .setSecurity(Security.AES_128_GCM)
                .setUser(new User(UUID.fromString("b831381d-6324-4d53-ad4f-8cda48b30811"),0));
        option.setName(name);
        return createServer(vmessRule,option,opt->new VmessProxyTransport(channelCreator, (VmessOption) opt));
    }

    @Rule
    public XrayRule vmessWsRule = new XrayRule();
    protected ProxyServer createVmessProxyWithWs(ChannelCreator channelCreator,String name) throws IOException {
        InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 16825);
        var option=new VmessOption()
                .setAddress(dest)
                .setSecurity(Security.AES_128_GCM)
                .setUser(new User(UUID.fromString("b831381d-6324-4d53-ad4f-8cda48b30811"),0))
                .setNetwork("ws")
                .setTransport(new TransportOption()
                        .setWs(new WsOption()))
                .setName(name);
        return createServer(vmessWsRule,option,opt->new VmessProxyTransport(channelCreator, (VmessOption) opt));
    }

    @Rule
    public XrayRule ssRule = new XrayRule();
    private ProxyServer createSsProxy(ChannelCreator channelCreator,String name) throws IOException {
        InetSocketAddress dest = Inet.createSocketAddress("127.0.0.1", 16827);
        var option=new ShadowsocksOption()
                .setAddress(dest)
                .setCipher(CipherAlgo.CHACHA20_IETF_POLY1305)
                .setPassword("passpasspass")
                .setName(name);
        return createServer(ssRule,option,opt->new ShadowsocksTransport(channelCreator, (ShadowsocksOption) opt));
    }

    @Rule
    public XrayRule ssWsRule = new XrayRule();
    protected ProxyServer createSsProxyWithWs(ChannelCreator channelCreator,String name) throws IOException {
        InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 16829);
        var option=new ShadowsocksOption()
                .setAddress(dest)
                .setCipher(CipherAlgo.CHACHA20_IETF_POLY1305)
                .setPassword("passpasspass")
                .setName(name)
                .setNetwork("ws")
                .setTransport(new TransportOption().setWs(new WsOption()));
        return createServer(ssWsRule,option,opt->new ShadowsocksTransport(channelCreator, (ShadowsocksOption) opt));
    }

    @Rule
    public XrayRule trojanRule = new XrayRule();
    protected ProxyServer createTrojanProxy(ChannelCreator channelCreator,String name) throws IOException {
        InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 16831);
        var option=new TrojanOption()
                .setAddress(dest)
                .setPassword("password")
                .setProtocol("trojan")
                .setTls(new TlsOption().setEnable(false))
                .setName(name);
        return createServer(trojanRule,option,opt->new TrojanProxyTransport(channelCreator, (TrojanOption) opt));
    }

    @Rule
    public XrayRule socksRule = new XrayRule();
    protected ProxyServer createSocksProxy(ChannelCreator channelCreator,String name) throws IOException {
        InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 16837);
        var option=new SocksOption()
                .setRemote(dest)
                .setName(name);
        return createServer(socksRule,option,opt->new SocksProxyTransport(channelCreator, (SocksOption) opt));
    }

    @Rule
    public XrayRule socksWsRule = new XrayRule();
    protected ProxyServer createSocksProxyWithWs(ChannelCreator channelCreator,String name) throws IOException {
        InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 16838);
        SocksOption option=new SocksOption()
                .setRemote(dest);
        option.setName(name)
              .setNetwork("ws")
              .setTransport(new TransportOption().setWs(new WsOption()));
        return createServer(socksWsRule,option,opt->new SocksProxyTransport(channelCreator, (SocksOption) opt));
    }

    @Rule
    public XrayRule vlessRule = new XrayRule();
    private ProxyServer createVlessProxy(ChannelCreator channelCreator,String name) throws IOException {
        InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 16839);
        var option=new VlessOption()
                .setAddress(dest)
                .setId("testtest")
                .setProtocol("vless")
                .setTls(new TlsOption().setEnable(false))
                .setName(name);
        return createServer(vlessRule,option,opt->new VlessProxyTransport(channelCreator, (VlessOption) opt));
    }

    @Rule
    public SshRule sshRule = new SshRule();
    protected ProxyServer createSshProxy(ChannelCreator channelCreator,String name) throws IOException, InterruptedException {
        SshOption sshOption = new SshOption();
        sshOption.setAddress(new InetSocketAddress("127.0.0.1",37432))
                 .setUser("user")
                 .setPassword("password")
                 .setVerifyStrategy(SshOption.VerifyStrategy.ACCEPT_ALL)
                 .setName(name);
        sshRule.start(sshOption);
        return new ProxyServer(
                new SshProxyTransport(channelCreator, sshRule.getOutside()),
                new SshProxyTransport(channelCreator, sshRule.getInside())
        );
    }

    private void setupSshServer() throws IOException {
        SshServer sshServer = SshServer.setUpDefaultServer();
        sshServer.setHost("127.0.0.1");
        sshServer.setPort(37432);
        sshServer.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        sshServer.setPasswordAuthenticator(((username, password, session) -> {
            if ("root".equals(username)||"password".equals(password)){
                return true;
            }else{
                return false;
            }
        }));
        sshServer.setForwardingFilter(AcceptAllForwardingFilter.INSTANCE);
        sshServer.start();
        Testcontainers.exposeHostPorts(sshServer.getPort());
    }

    private ProxyTransport createChainProxyTransport(List<ProxyServer> proxyTransport){
        List<NodeType> nodeTypes=new ArrayList<>();
        proxyTransport.stream().map(it->it.header.getTag()).forEach(it->nodeTypes.add(new NodeType.Name(it)));

        Map<String, ProxyTransport> transportMap =
                proxyTransport.stream().skip(1).collect(Collectors.toMap(it->it.node.getTag(), it->it.node));

        ProxyTransport header = proxyTransport.getFirst().header;
        transportMap.put(header.getTag(), header);

        ChainOption chainOption = new ChainOption().setNodes(nodeTypes);
        chainOption.setName("chain");
        ChainProxyTransport transport = new ChainProxyTransport(channelCreator, chainOption);
        transport.initTransport(new ProxyTransportProvider() {
            @Override
            public ProxyTransport get(String name) {
                return transportMap.get(name);
            }

            @Override
            public ProxyTransport create(ProtocolOption protocolOption) {
                return null;
            }
        });
        return transport;
    }

    private void allCombination(List<ProxyServer> transports, List<ProxyServer> tmp, int idx, Consumer<List<ProxyServer>> tmpHandler){
        if (idx >= transports.size()){
            return;
        }
        tmp.add(transports.get(idx));
        if (tmp.size()== transports.size()) {
            System.out.println(tmp.stream()
                                  .map(it->it.header.getTag())
                                  .collect(Collectors.joining(",")));
            tmpHandler.accept(tmp);
        }
        for (int i = 0; i < transports.size(); i++) {
            if (!tmp.contains(transports.get(i))){
                allCombination(transports, tmp, i,tmpHandler);
            }
        }
        tmp.remove(transports.get(idx));
    }

    @Test
    public void tcpTest() throws Exception {
        setupSshServer();
        var list = new ArrayList<>(List.of(createVmessProxy(channelCreator,"vmess0"),createSsProxy(channelCreator,"ss0"),
                createTrojanProxy(channelCreator,"trojan0"),createVlessProxy(channelCreator,"vless0"),createSshProxy(channelCreator,"ssh0"),
                createSocksProxy(channelCreator,"socks0")));
        Collections.shuffle(list);
        System.out.println(list.stream().map(it->it.header.getTag()).collect(Collectors.joining(",")));
//        List<ProxyTransport> proxies = new ArrayList<>();
//        for (int i = 0; i < list.size(); i++) {
//            all(list,proxies,i, Lambdas.rethrowConsumer(it->{
//                tcpTest(createChainProxyTransport(it));
//            }));
//        }

        tcpTest(createChainProxyTransport(list));

//        tcpTest(createChainProxyTransport(list));
    }

    @Test
    public void wsTest() throws Exception {
        var list = List.of(
                createVmessProxyWithWs(channelCreator,"ws-vmess0"),
                createSsProxyWithWs(channelCreator,"ws-ss0"),
                createSocksProxyWithWs(channelCreator,"ws-socks0"));

        for (int i = 0; i < list.size(); i++) {
            allCombination(list,new ArrayList<>(),i, Lambdas.rethrowConsumer(it->{
                tcpTest(createChainProxyTransport(it));
            }));
        }
    }

    @Test
    public void udpTest() throws Exception {
        var list = List.of(createSsProxy(channelCreator,"ss0"),createVmessProxy(channelCreator,"vmess0"));

        for (int i = 0; i < list.size(); i++) {
            allCombination(list,new ArrayList<>(),i, Lambdas.rethrowConsumer(it->{
                udpTest(createChainProxyTransport(it));
            }));
        }
    }
}
