package io.crowds.proxy.services.http;

import io.crowds.proxy.Axis;
import io.crowds.util.Inet;
import io.crowds.util.Ints;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class HttpServerHandler extends ChannelInitializer<Channel> {
    protected final HttpOption httpOption;
    protected final Axis axis;

    protected HttpServerHandler(HttpOption httpOption, Axis axis) {
        this.httpOption = httpOption;
        this.axis = axis;
    }


    protected boolean isUdpRequest(URI uri){
        return uri.getRawPath().startsWith(httpOption.getUdpPath());
    }
    protected InetSocketAddress extractUdpTargetFromUri(URI uri){
        String udpPath = httpOption.getUdpPath();
        QueryStringDecoder decoder = QueryStringDecoder.builder().build(uri);
        String subPath = decoder.path().substring(udpPath.length());
        if (!subPath.isEmpty()){
            if (subPath.startsWith("/")){
                subPath = subPath.substring(1);
            }
            if (subPath.endsWith("/")){
                subPath = subPath.substring(0,subPath.length()-1);
            }
            String[] split = subPath.split("/");
            if (split.length!=2){
                return null;
            }
            try {
                return Inet.createSocketAddress(split[0], Integer.parseInt(split[1]));
            } catch (NumberFormatException e) {
                return null;
            }
        }else{
            Map<String, List<String>> parameters = decoder.parameters();
            List<String> h = Objects.requireNonNullElse(parameters.get("h"),List.of());
            List<String> p = Objects.requireNonNullElse(parameters.get("p"),List.of());
            if (!h.isEmpty()&&!p.isEmpty()){
                try {
                    var host = h.getFirst();
                    var port = Integer.parseInt(p.getFirst());
                    if (!Ints.isAvailablePort(port)){
                        return null;
                    }
                    return Inet.createSocketAddress(host,port);
                } catch (NumberFormatException e) {
                    return null;
                }
            }else{
                String query = decoder.rawQuery();
                if (!query.contains(",")) {
                    return null;
                }
                String[] split = query.split(",");
                if (split.length!=2){
                    return null;
                }
                try {
                    return Inet.createSocketAddress(QueryStringDecoder.decodeComponent(split[0]), Integer.parseInt(split[1]));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
    }
}
