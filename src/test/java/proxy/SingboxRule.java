package proxy;

import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.TlsOption;
import io.crowds.proxy.transport.proxy.shadowsocks.ShadowsocksOption;
import io.crowds.proxy.transport.proxy.socks.SocksOption;
import io.crowds.proxy.transport.proxy.trojan.TrojanOption;
import io.crowds.proxy.transport.proxy.tuic.TuicOption;
import io.crowds.proxy.transport.proxy.vless.VlessOption;
import io.crowds.proxy.transport.proxy.vmess.VmessOption;
import io.crowds.proxy.transport.ws.WsOption;
import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.Transferable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.stream.Collectors;

public class SingboxRule extends AbstractRule {

    private static final Logger logger = LoggerFactory.getLogger(SingboxRule.class);
    private GenericContainer<?> server;
    private String name;


    private ProtocolOption inside;
    private ProtocolOption outside;

    public SingboxRule(Network network) {
        super(network);
    }

    public SingboxRule setName(String name) {
        this.name = name;
        return this;
    }

    public ProtocolOption getOutside() {
        return outside;
    }

    public ProtocolOption getInside() {
        return inside;
    }

    private JsonObject buildTls(ProtocolOption protocolOption) throws Exception {

        TlsOption tls = protocolOption.getTls();
        if (tls==null) {
            return null;
        }
        if (!tls.isEnable()) {
            return null;
        }
        JsonObject json = new JsonObject();
        X509Bundle x509Bundle = new CertificateBuilder()
                .subject("cn=localhost")
                .setIsCertificateAuthority(true)
                .buildSelfSigned();
        Random random = new Random();
        String prefix = random.ints(8, '1', 'Z')
                .boxed().map(Objects::toString).collect(Collectors.joining());
        String certChainPath = "/tmp/"+ prefix +"-chain.pem";
        String priKeyPath = "/tmp/"+ prefix +"-key.pem";
        server.withCopyToContainer(Transferable.of(x509Bundle.getCertificatePathPEM(),777),
                certChainPath);
        server.withCopyToContainer(Transferable.of(x509Bundle.getPrivateKeyPEM(),777),
                priKeyPath);
        json.put("enabled", true)
                .put("insecure", tls.isAllowInsecure())
                .put("alpn",new JsonArray().add("h3"))
                .put("certificate_path",certChainPath)
                .put("key_path",priKeyPath)
                ;

        return json;
    }

    private JsonObject buildTransport(ProtocolOption protocolOption){
        if (!Objects.equals(protocolOption.getNetwork(), "ws")) {
            return null;
        }
        JsonObject json = new JsonObject();
        json.put("type","ws");
        WsOption ws = protocolOption.getTransport().getWs();
        if (ws !=null){
            var wsSettings = new JsonObject();
            if (ws.getPath()!=null){
                wsSettings.put("path",ws.getPath());
            }
            if (ws.getHeaders()!=null){
                var headers = new JsonObject();
                for (Map.Entry<String, String> entry : ws.getHeaders()) {
                    headers.put(entry.getKey(),entry.getValue());
                }
                json.put("headers",headers);
            }
        }
        return json;
    }


    private JsonObject buildSingboxConfig(ProtocolOption protocolOption, GenericContainer<?> container) throws Exception {

        JsonArray inbounds = new JsonArray();
        switch (protocolOption) {

            case TuicOption tuic->{
                container.setPortBindings(List.of(
                        tuic.getAddress().getPort() + ":" + tuic.getAddress().getPort() + "/tcp",
                        tuic.getAddress().getPort() + ":" + tuic.getAddress().getPort() + "/udp"
                ));
                inbounds.add(
                        new JsonObject()
                                .put("type","tuic")
                                .put("listen","0.0.0.0")
                                .put("listen_port",tuic.getAddress().getPort())
                                .put("users",
                                        new JsonArray().add(
                                                new JsonObject()
                                                        .put("uuid",tuic.getUuid().toString())
                                                        .put("password",tuic.getPassword())
                                        )
                                )
                                .put("tls",buildTls(tuic))
                );
            }
            default -> throw new IllegalArgumentException(protocolOption.getClass().getName() + " is not supported");
        }
        JsonObject configFile = new JsonObject();
        configFile.put("log",new JsonObject().put("level","debug").put("timestamp",true));
        configFile.put("inbounds",inbounds);
        configFile.put("outbounds",new JsonArray()
                .add(
                        new JsonObject()
                                .put("type","direct")
                )
        );
        return configFile;

    }


    private void fixOption(ProtocolOption protocolOption, GenericContainer<?> server){
        System.out.println(server.getExtraHosts());
        var insideHost = Optional.ofNullable(name)
                .orElseGet(()->Optional.ofNullable(server.getNetworkAliases())
                                       .filter(it->!it.isEmpty())
                                       .map(List::getFirst)
                                       .orElseGet(server::getContainerName));
        if (protocolOption instanceof TuicOption tuic){
            this.inside = new TuicOption(tuic).setAddress(InetSocketAddress.createUnresolved(insideHost,tuic.getAddress().getPort()));
            this.outside = tuic;
        }
    }

    public synchronized ProtocolOption start(ProtocolOption protocolOption) throws IOException {
        if (server!=null){
            throw new IllegalStateException("Xray server already started");
        }
        GenericContainer<?> container = new GenericContainer<>("superng6/singbox:v1.12.19");
        server = container;
        JsonObject sbConfig = null;
        try {
            sbConfig = buildSingboxConfig(protocolOption, container);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        logger.info("singbox config: {}",sbConfig.encodePrettily());
        if (name==null) {
            this.name = protocolOption.getName();
        }
        container.withNetwork(network)
                 .withNetworkAliases(name)
                 .withCopyToContainer(Transferable.of(sbConfig.encode(), 777),"/etc/sing-box/config.json")
                 .withLogConsumer(new Slf4jLogConsumer(logger))
                 .withAccessToHost(true);
        container.withCommand("run -c /etc/sing-box/config.json");

        server.start();
        fixOption(protocolOption, server);
        return protocolOption;
    }



    @Override
    protected void after() {
        if (server!=null){
            server.stop();
            server=null;
        }
    }


}
