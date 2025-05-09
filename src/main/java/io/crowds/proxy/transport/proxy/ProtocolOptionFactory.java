package io.crowds.proxy.transport.proxy;

import io.crowds.compoments.wireguard.PeerOption;
import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.TlsOption;
import io.crowds.proxy.transport.TransportOption;
import io.crowds.proxy.transport.proxy.chain.ChainOption;
import io.crowds.proxy.transport.proxy.chain.NodeType;
import io.crowds.proxy.transport.proxy.shadowsocks.CipherAlgo;
import io.crowds.proxy.transport.proxy.shadowsocks.ShadowsocksOption;
import io.crowds.proxy.transport.proxy.socks.SocksOption;
import io.crowds.proxy.transport.proxy.ssh.SshOption;
import io.crowds.proxy.transport.proxy.trojan.TrojanOption;
import io.crowds.proxy.transport.proxy.vless.VlessOption;
import io.crowds.proxy.transport.proxy.vless.VlessUUID;
import io.crowds.proxy.transport.proxy.vmess.Security;
import io.crowds.proxy.transport.proxy.vmess.User;
import io.crowds.proxy.transport.proxy.vmess.VmessOption;
import io.crowds.proxy.transport.proxy.wireguard.WireguardOption;
import io.crowds.proxy.transport.ws.WsOption;
import io.crowds.util.IPCIDR;
import io.crowds.util.Inet;
import io.crowds.util.Strs;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stormpx.net.util.IP;
import org.stormpx.net.util.SubNet;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.IntStream;

public class ProtocolOptionFactory {
    private final static Logger logger= LoggerFactory.getLogger(ProtocolOptionFactory.class);
    private static TlsOption parseTls(JsonObject json){
        if (json==null)
            return null;
        TlsOption tlsOption = new TlsOption();
        var tls=json.getBoolean("enable",false);
        var tlsAllowInsecure=json.getBoolean("allowInsecure",false);
        var tlsServerName=json.getString("serverName");
        tlsOption.setEnable(tls)
                .setAllowInsecure(tlsAllowInsecure)
                .setServerName(tlsServerName);

        return tlsOption;

    }

    private static WsOption parseWs(JsonObject json){
        if (json==null)
            return null;
        WsOption wsOption = new WsOption();
        String path = json.getString("path","/");
        wsOption.setPath(path);
        JsonObject headersJson = json.getJsonObject("headers");
        if (headersJson!=null){
            HttpHeaders headers = new DefaultHttpHeaders();
            for (Map.Entry<String, Object> entry : headersJson) {
                headers.add(entry.getKey(),entry.getValue());
            }
            wsOption.setHeaders(headers);
        }

        return wsOption;
    }

    private static VmessOption parseVmess(JsonObject json){
        VmessOption vmessOption = new VmessOption();
        var host=json.getString("host");
        var port=json.getInteger("port");
        vmessOption.setAddress(Inet.createSocketAddress(host,port));
        String uuid = json.getString("uid");
        Integer alterId = json.getInteger("alterId",0);
        if (Strs.isBlank(uuid)) {
            throw new NullPointerException("uid is required.");
        }
        vmessOption.setUser(new User(UUID.fromString(uuid),alterId));

        String securityStr = json.getString("security");
        Security security = Security.of(securityStr);
        vmessOption.setSecurity(security);

        return vmessOption;
    }

    private static ShadowsocksOption parseSs(JsonObject json){

        ShadowsocksOption shadowsocksOption = new ShadowsocksOption();
        var host=json.getString("host");
        var port=json.getInteger("port");
        InetSocketAddress address = Inet.createSocketAddress(host, port);
        String cipherStr = json.getString("cipher");
        CipherAlgo cipher = CipherAlgo.of(cipherStr);
        if (cipher==null){
            throw new IllegalArgumentException("invalid cipher: "+cipherStr);
        }
        String password = json.getString("password");
        if (Strs.isBlank(password)){
            throw new IllegalArgumentException("password is required");
        }
        shadowsocksOption.setAddress(address)
                .setCipher(cipher)
                .setPassword(password);
        return shadowsocksOption;
    }

    private static TrojanOption parseTrojan(JsonObject json){

        TrojanOption trojanOption = new TrojanOption();
        var host=json.getString("host");
        var port=json.getInteger("port");

        InetSocketAddress address = Inet.createSocketAddress(host,port);
        String password = json.getString("password");
        if (Strs.isBlank(password)){
            throw new IllegalArgumentException("password is required");
        }
        trojanOption.setAddress(address)
                .setPassword(password);
        return trojanOption;
    }

    private static SocksOption parseSocks(JsonObject json){
        SocksOption socksOption = new SocksOption();
        var host=json.getString("host");
        var port=json.getInteger("port");

        InetSocketAddress remote = Inet.createSocketAddress(host,port);
        socksOption.setRemote(remote);
        return socksOption;
    }

    private static VlessOption parseVless(JsonObject json){
        VlessOption vlessOption = new VlessOption();
        var host=json.getString("host");
        var port=json.getInteger("port");
        InetSocketAddress address = Inet.createSocketAddress(host,port);
        String id = json.getString("id");

        vlessOption.setAddress(address)
                .setId(VlessUUID.of(id));
        return vlessOption;
    }

    private static SshOption parseSsh(JsonObject json){
        SshOption sshOption = new SshOption();
        var host=json.getString("host");
        var port=json.getInteger("port");
        InetSocketAddress address = Inet.createSocketAddress(host,port);
        String user = json.getString("user");
        if (Strs.isBlank(user)) {
            throw new IllegalArgumentException("user is required");
        }
        String password = json.getString("password");
        String privateKey = json.getString("privateKey");
        String passphrase = json.getString("passphrase");
        String serverKey = json.getString("serverKey");
        String verify = json.getString("verify");
        sshOption.setAddress(address)
                .setUser(user)
                .setPassword(password)
                .setPrivateKey(Strs.isBlank(privateKey)?null:Path.of(privateKey))
                .setPassphrase(passphrase)
                .setServerKey(Strs.isBlank(serverKey)?null:Path.of(serverKey))
                .setVerifyStrategy(SshOption.VerifyStrategy.valueOf0(verify))
        ;

        return sshOption;
    }

    private static SubNet parseSubNet(String address){
        int index = address.indexOf("/");
        if (index==-1){
            return new SubNet(IP.parse(address),32);
        }
        IP ip = IP.parse(address.substring(0, index));
        int mask = Integer.parseInt(address.substring(index + 1));
        return new SubNet(ip,mask);
    }
    private static PeerOption parseWgPeer(String name,int idx, JsonObject peerConfig){
        String publicKey = peerConfig.getString("publicKey");
        String perSharedKey = peerConfig.getString("perSharedKey");
        String allowedIp = peerConfig.getString("allowedIp");
        String endpoint = peerConfig.getString("endpoint");
        Integer keepAlive = peerConfig.getInteger("keepAlive");
        if (Strs.isBlank(publicKey)){
            throw new IllegalArgumentException("wireGuard config: %s[%d].peer.publicKey is required.".formatted(name,idx));
        }
        if (Strs.isBlank(perSharedKey)){
            throw new IllegalArgumentException("wireGuard config: %s[%d].peer.perSharedKey is required.".formatted(name,idx));
        }
        if (Strs.isBlank(allowedIp)){
            throw new IllegalArgumentException("wireGuard config: %s[%d].peer.allowedIp is required.".formatted(name,idx));
        }
        if (Strs.isBlank(endpoint)){
            throw new IllegalArgumentException("wireGuard config: %s[%d].peer.endpoint is required.".formatted(name,idx));
        }
        return new PeerOption(publicKey,perSharedKey, new IPCIDR(allowedIp), Objects.requireNonNullElse(keepAlive,0).shortValue(),
                Inet.parseInetAddress(endpoint));
    }

    private static WireguardOption parseWireguard(JsonObject json){
        WireguardOption wireguardOption = new WireguardOption();
        String privateKey = json.getString("privateKey");
        String address = json.getString("address");
        String dns = json.getString("dns");
        if (Strs.isBlank(privateKey)){
            throw new IllegalArgumentException("wireGuard privateKey is required.");
        }
        if (Strs.isBlank(address)){
            throw new IllegalArgumentException("wireGuard address is required.");
        }
        wireguardOption.setPrivateKey(privateKey);
        wireguardOption.setAddress(parseSubNet(address));
        if (!Strs.isBlank(dns)){
            try {
                wireguardOption.setDns(Inet.parseInetAddress(dns));
            } catch (Exception e) {
                logger.error("Exception during parse wireguard dns: {}",e.getMessage());
            }
        }

        JsonArray peersArray = json.getJsonArray("peers");
        var peers = IntStream.range(0,peersArray.size())
                             .filter(idx->peersArray.getValue(idx) instanceof JsonObject)
                             .mapToObj(idx->parseWgPeer(json.getString("name"),idx,peersArray.getJsonObject(idx)))
                             .toList();

        wireguardOption.setPeers(peers);

        return wireguardOption;
    }

    private static ChainOption parseChain(JsonObject json){
        ChainOption chainOption = new ChainOption();
        JsonArray nodes = json.getJsonArray("nodes");
        List<NodeType> nodeTypes=new ArrayList<>();
        for (Object item : nodes) {
            if (item instanceof String str){
                nodeTypes.add(new NodeType.Name(str));
            }else if (item instanceof JsonObject jsonObject){
                ProtocolOption subOption = newOption(jsonObject);
                if (subOption!=null) {
                    if (subOption instanceof ChainOption subChainOption){
                        for (NodeType node : subChainOption.getNodes()) {
                            if (node instanceof NodeType.Name){
                                throw new IllegalArgumentException("Nested chains directly specifying a proxy is not allowed.");
                            }
                        }
                    }
                    nodeTypes.add(new NodeType.Option(subOption));
                }
            }else{
                logger.warn("{} unrecognized node config",item.getClass().getSimpleName());
            }
        }
        chainOption.setNodes(nodeTypes);
        return chainOption;
    }

    public static ProtocolOption newOption(JsonObject json){
        var name = json.getString("name");
        if (Strs.isBlank(name)){
            throw new RuntimeException("proxies option names is required");
        }
        var protocol = json.getString("protocol");
        try {
            var connIdle = json.getInteger("connIdle");
            String network = json.getString("network");
            JsonObject tls = json.getJsonObject("tls");
            JsonObject transportJson = json.getJsonObject("transport");
            ProtocolOption protocolOption = null;
            if ("vmess".equalsIgnoreCase(protocol)){
                protocolOption=parseVmess(json);
            }else if ("ss".equalsIgnoreCase(protocol)||"shadowsocks".equals(protocol)){
                protocolOption=parseSs(json);
                protocolOption.setProtocol("ss");
            }else if ("trojan".equalsIgnoreCase(protocol)){
                protocolOption=parseTrojan(json);
            }else if ("socks".equalsIgnoreCase(protocol)){
                protocolOption=parseSocks(json);
            }else if ("vless".equalsIgnoreCase(protocol)) {
                protocolOption=parseVless(json);
            }else if("ssh".equalsIgnoreCase(protocol)){
                protocolOption=parseSsh(json);
            }else if ("wg".equals(protocol)||"wireguard".equals(protocol)){
                protocolOption=parseWireguard(json);
                protocolOption.setProtocol("wg");
            }else if ("chain".equals(protocol)){
                protocolOption=parseChain(json);
            }else if ("direct".equalsIgnoreCase(protocol)){
                protocolOption=new ProtocolOption();
            }
            if (protocolOption!=null){
                protocolOption.setProtocol(protocol)
                        .setName(name)
                        .setNetwork(network)
                ;
                if (connIdle!=null){
                    protocolOption.setConnIdle(connIdle<0?0:connIdle);
                }
                if (tls!=null){
                    protocolOption.setTls(parseTls(tls));
                }
                if (transportJson!=null){
                    TransportOption transportOption = new TransportOption();
                    transportOption.setDev(transportJson.getString("dev"));
                    transportOption.setWs(parseWs(transportJson.getJsonObject("ws")));

                    protocolOption.setTransport(transportOption);
                }
                return protocolOption;
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("unable parse %s option.".formatted(protocol),e);
        }
    }

}
