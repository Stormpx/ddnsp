package proxy;

import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.TlsOption;
import io.crowds.proxy.transport.proxy.shadowsocks.ShadowsocksOption;
import io.crowds.proxy.transport.proxy.socks.SocksOption;
import io.crowds.proxy.transport.proxy.trojan.TrojanOption;
import io.crowds.proxy.transport.proxy.vless.VlessOption;
import io.crowds.proxy.transport.proxy.vmess.VmessOption;
import io.crowds.proxy.transport.ws.WsOption;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.Transferable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class XrayRule extends ExternalResource {

    private static final Logger logger = LoggerFactory.getLogger(XrayRule.class);
    private GenericContainer<?> server;
    private String name;

    private ProtocolOption inside;
    private ProtocolOption outside;

    public XrayRule setName(String name) {
        this.name = name;
        return this;
    }

    public ProtocolOption getOutside() {
        return outside;
    }

    public ProtocolOption getInside() {
        return inside;
    }

    private JsonObject buildStreamSettings(ProtocolOption protocolOption){
        JsonObject json = new JsonObject();
        TlsOption tls = protocolOption.getTls();
        if (tls!=null) {
            if (tls.isEnable()) {
                json.put("security", "tls")
                    .put("allowInsecure", tls.isAllowInsecure());
            }
        }
        if (Objects.equals(protocolOption.getNetwork(),"ws")){
            json.put("network","ws");
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
                }
                json.put("wsSettings",wsSettings);
            }
        }
        return json;
    }

    private JsonObject buildXrayConfig(ProtocolOption protocolOption,GenericContainer<?> container){

        JsonArray inbounds = new JsonArray();
        switch (protocolOption) {
            case VmessOption vmess -> {
                container.withExposedPorts(vmess.getAddress()
                                                .getPort());
                inbounds.add(new JsonObject().put("protocol", "vmess")
                                             .put("port", vmess.getAddress()
                                                               .getPort())
                                             .put("settings", new JsonObject().put("clients", new JsonArray().add(
                                                     new JsonObject().put("id", vmess.getUser()
                                                                                     .getPrimaryUser()
                                                                                     .getUuid())
                                                                     .put("alterId", vmess.getUser()
                                                                                          .getAlterId()))))
                                             .put("streamSettings", buildStreamSettings(protocolOption)));
            }
            case VlessOption vless -> {
                container.withExposedPorts(vless.getAddress()
                                                .getPort());
                inbounds.add(new JsonObject().put("protocol", "vless")
                                             .put("port", vless.getAddress()
                                                               .getPort())
                                             .put("settings", new JsonObject().put("clients",
                                                                                      new JsonArray().add(new JsonObject().put("id", vless.getId())))
                                                                              .put("decryption", "none"))
                                             .put("streamSettings", buildStreamSettings(protocolOption)));
            }
            case TrojanOption trojan -> {
                container.withExposedPorts(trojan.getAddress()
                                                 .getPort());
                inbounds.add(new JsonObject().put("protocol", "trojan")
                                             .put("port", trojan.getAddress()
                                                                .getPort())
                                             .put("settings", new JsonObject().put("clients", new JsonArray().add(
                                                     new JsonObject().put("password", trojan.getOriginPassword()))))
                                             .put("streamSettings", buildStreamSettings(protocolOption)));
            }
            case SocksOption socks -> {
                container.setPortBindings(List.of(
                        socks.getRemote().getPort() + ":" + socks.getRemote().getPort() + "/tcp",
                        socks.getRemote().getPort() + ":" + socks.getRemote().getPort() + "/udp"
                ));
                inbounds.add(new JsonObject().put("protocol", "socks")
                                             .put("port", socks.getRemote()
                                                               .getPort())
                                             .put("settings", new JsonObject().put("auth", "noauth")
                                                                              .put("udp", true)
                                                                              .put("ip", "127.0.0.1"))
                                             .put("streamSettings", buildStreamSettings(protocolOption)));
            }
            case ShadowsocksOption ss -> {
                container.setPortBindings(List.of(
                        ss.getAddress().getPort() + ":" + ss.getAddress().getPort() + "/tcp",
                        ss.getAddress().getPort() + ":" + ss.getAddress().getPort() + "/udp"
                ));
                inbounds.add(new JsonObject().put("protocol", "shadowsocks")
                                             .put("port", ss.getAddress()
                                                            .getPort())
                                             .put("settings", new JsonObject().put("network", "tcp,udp")
                                                                              .put("method", ss.getCipher()
                                                                                               .getName())
                                                                              .put("password", ss.getPassword()))
                                             .put("streamSettings", buildStreamSettings(protocolOption)));
            }
            default -> throw new IllegalArgumentException(protocolOption.getClass().getName() + " is not supported");
        }
        JsonObject configFile = new JsonObject();
        configFile.put("log",new JsonObject().put("logLevel","debug"));
        configFile.put("inbounds",inbounds);
        configFile.put("outbounds",new JsonArray()
                .add(
                        new JsonObject()
                                .put("protocol","freedom")
                                .put("settings",new JsonObject())
                )
        );
        return configFile;

    }


    private void fixOption(ProtocolOption protocolOption, GenericContainer<?> server){
        var insideHost = Optional.ofNullable(name)
                .orElseGet(()->Optional.ofNullable(server.getNetworkAliases())
                                       .filter(it->!it.isEmpty())
                                       .map(List::getFirst)
                                       .orElseGet(server::getContainerName));
        if (protocolOption instanceof VmessOption vmess){
            this.inside = new VmessOption(vmess).setAddress(InetSocketAddress.createUnresolved(insideHost,vmess.getAddress().getPort()));
            this.outside = vmess.setAddress(new InetSocketAddress(server.getHost(),server.getFirstMappedPort()));
        }else if (protocolOption instanceof VlessOption vless){
            this.inside = new VlessOption(vless).setAddress(InetSocketAddress.createUnresolved(insideHost,vless.getAddress().getPort()));
            this.outside = vless.setAddress(new InetSocketAddress(server.getHost(),server.getFirstMappedPort()));
        }else if (protocolOption instanceof TrojanOption trojan){
            this.inside = new TrojanOption(trojan).setAddress(InetSocketAddress.createUnresolved(insideHost,trojan.getAddress().getPort()));
            this.outside = trojan.setAddress(new InetSocketAddress(server.getHost(),server.getFirstMappedPort()));
        }else if (protocolOption instanceof SocksOption socks){
            this.inside = new SocksOption(socks).setRemote(InetSocketAddress.createUnresolved(insideHost,socks.getRemote().getPort()));
            this.outside = socks;
        }else if (protocolOption instanceof ShadowsocksOption ss){
            this.inside = new ShadowsocksOption(ss).setAddress(InetSocketAddress.createUnresolved(insideHost,ss.getAddress().getPort()));
            this.outside = ss;
        }
    }

    public synchronized ProtocolOption start(ProtocolOption protocolOption) throws IOException {
        if (server!=null){
            throw new IllegalStateException("Xray server already started");
        }
        GenericContainer<?> container = new GenericContainer<>("teddysun/xray");
        JsonObject xrayConfig = buildXrayConfig(protocolOption, container);
        logger.info("xray config: {}",xrayConfig.encodePrettily());
        if (name==null) {
            this.name = protocolOption.getName();
        }
        container.withNetwork(ProxyTestBase.CONTAINER_NETWORK)
                 .withNetworkAliases(name)
                 .withCopyToContainer(Transferable.of(xrayConfig.encode(), 777),"/etc/xray/config.json")
                 .withLogConsumer(new Slf4jLogConsumer(logger))
                 .withAccessToHost(true);


        server = container;
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
