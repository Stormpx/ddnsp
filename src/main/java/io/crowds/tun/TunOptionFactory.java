package io.crowds.tun;

import io.crowds.tun.wireguard.PeerOption;
import io.crowds.tun.wireguard.WireGuardOption;
import io.crowds.util.IPCIDR;
import io.crowds.util.Inet;
import io.crowds.util.Strs;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Objects;

public class TunOptionFactory {

    private static WireGuardOption parseWg(JsonObject config){
        WireGuardOption option = new WireGuardOption();
        String privateKey = config.getString("privateKey");
        if (Strs.isBlank(privateKey)){
            throw new IllegalArgumentException("wireGuard privateKey is required.");
        }
        JsonArray peersArray = config.getJsonArray("peers");
        for (int i = 0; i < peersArray.size(); i++) {
            if (peersArray.getValue(i) instanceof JsonObject peerConfig){
                String publicKey = peerConfig.getString("publicKey");
                String perSharedKey = peerConfig.getString("perSharedKey");
                String allowedIp = peerConfig.getString("allowedIp");
                String endpoint = peerConfig.getString("endpoint");
                Integer keepAlive = peerConfig.getInteger("keepAlive");
                if (Strs.isBlank(publicKey)){
                    throw new IllegalArgumentException("wireGuard[%d].peer.publicKey is required.".formatted(i));
                }
                if (Strs.isBlank(perSharedKey)){
                    throw new IllegalArgumentException("wireGuard[%d].peer.perSharedKey is required.".formatted(i));
                }
                if (Strs.isBlank(allowedIp)){
                    throw new IllegalArgumentException("wireGuard[%d].peer.allowedIp is required.".formatted(i));
                }
                if (Strs.isBlank(endpoint)){
                    throw new IllegalArgumentException("wireGuard[%d].peer.endpoint is required.".formatted(i));
                }

                new PeerOption(publicKey,perSharedKey, new IPCIDR(allowedIp), Objects.requireNonNullElse(keepAlive,0).shortValue(),
                        Inet.parseInetAddress(endpoint));
            }
        }
        return option;
    }

    public static TunOption newTunOption(JsonObject config){
        String name = config.getString("name");
        String addr = config.getString("addr");
        Integer mtu = config.getInteger("mtu",1500);
        String type = config.getString("type");
        if (Strs.isBlank(name)){
            throw new IllegalArgumentException("tun name is required");
        }
        if (Strs.isBlank(addr)){
            throw new IllegalArgumentException("tun addr is required");
        }
        if (Strs.isBlank(type)){
            throw new IllegalArgumentException("tun type is required");
        }
        TunOption tunOption = switch (type){
            case "wg" -> parseWg(config);
            default -> throw new IllegalArgumentException("tun not supports type: "+type);
        };
        tunOption.setName(name)
                .setIpcidr(new IPCIDR(addr))
                .setMtu(mtu);

        return tunOption;

    }

}
