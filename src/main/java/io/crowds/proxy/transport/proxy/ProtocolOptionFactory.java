package io.crowds.proxy.transport.proxy;

import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.TlsOption;
import io.crowds.proxy.transport.TransportOption;
import io.crowds.proxy.transport.proxy.shadowsocks.CipherAlgo;
import io.crowds.proxy.transport.proxy.shadowsocks.ShadowsocksOption;
import io.crowds.proxy.transport.proxy.socks.SocksOption;
import io.crowds.proxy.transport.proxy.trojan.TrojanOption;
import io.crowds.proxy.transport.proxy.vmess.Security;
import io.crowds.proxy.transport.proxy.vmess.User;
import io.crowds.proxy.transport.proxy.vmess.VmessOption;
import io.crowds.proxy.transport.ws.WsOption;
import io.crowds.util.Inet;
import io.crowds.util.Strs;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.vertx.core.json.JsonObject;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;

public class ProtocolOptionFactory {

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

    public static ProtocolOption newOption(JsonObject json){
        var protocol = json.getString("protocol");
        try {
            var name = json.getString("name");
            var connIdle = json.getInteger("connIdle");
            String network = json.getString("network");
            JsonObject tls = json.getJsonObject("tls");
            JsonObject transportJson = json.getJsonObject("transport");
            ProtocolOption protocolOption = null;
            if ("vmess".equalsIgnoreCase(protocol)){
                protocolOption=parseVmess(json);
            }else if ("ss".equalsIgnoreCase(protocol)){
                protocolOption=parseSs(json);
            }else if ("trojan".equalsIgnoreCase(protocol)){
                protocolOption=parseTrojan(json);
            }else if ("socks".equalsIgnoreCase(protocol)){
                protocolOption=parseSocks(json);
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
