package io.crowds.proxy.transport.proxy.socks;

import io.crowds.proxy.DomainNetAddr;
import io.crowds.proxy.NetAddr;
import io.crowds.proxy.TP;
import io.crowds.proxy.common.HandlerName;
import io.crowds.proxy.transport.Destination;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.v5.*;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.util.UUID;


public class SocksClientNegotiator extends SimpleChannelInboundHandler<SocksMessage> {
    private final HandlerName baseName;
    private final Channel channel;
    private final Destination dst;
    private Promise<NetAddr> promise;

    private boolean start;

    public SocksClientNegotiator(HandlerName baseName,Channel channel, Destination dst) {
        if (baseName==null){
            baseName = new HandlerName("socks");
        }
        this.baseName=baseName;
        this.channel = channel;
        this.dst =dst;
        init();
    }

    private void init(){
        channel.pipeline()
               .addLast(baseName.with("encoder"),Socks5ClientEncoder.DEFAULT)
               .addLast(baseName.with("decoder"),new Socks5InitialResponseDecoder())
               .addLast(baseName.with("negotiator"),this);
        this.promise=channel.eventLoop().newPromise();
    }

    private void cleanChannelHandler(){
        this.channel.pipeline().remove(baseName.with("encoder"));
        this.channel.pipeline().remove(baseName.with("decoder"));
        this.channel.pipeline().remove(baseName.with("negotiator"));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SocksMessage msg) throws Exception {
        if (msg instanceof Socks5InitialResponse response){
            if (response.decoderResult().isFailure()){
                promise.tryFailure(response.decoderResult().cause());
                return;
            }
            if (response.authMethod()!=Socks5AuthMethod.NO_AUTH){
                promise.tryFailure(new SocksClientException(response.authMethod().toString()));
                return;
            }
            String decoderName = baseName.with("decoder");
            this.channel.pipeline().remove(decoderName);
            this.channel.pipeline().addBefore(baseName.with("negotiator"),decoderName,new Socks5CommandResponseDecoder());

            var commandType = dst.tp()== TP.TCP?Socks5CommandType.CONNECT:Socks5CommandType.UDP_ASSOCIATE;
            NetAddr addr = dst.addr();
            var addrType = switch (addr){
                case DomainNetAddr ignored -> Socks5AddressType.DOMAIN;
                default -> addr.isIpv4()?Socks5AddressType.IPv4:Socks5AddressType.IPv6;
            };
            this.channel.writeAndFlush(new DefaultSocks5CommandRequest(commandType,addrType, addr.getHost(),
                    addr.getPort()));

        }else if (msg instanceof Socks5CommandResponse response){
            if (response.decoderResult().isFailure()){
                promise.tryFailure(response.decoderResult().cause());
                return;
            }
            if (response.status()!=Socks5CommandStatus.SUCCESS){
                promise.tryFailure(new SocksClientException("command response "+response.status().toString()));
                return;
            }
            cleanChannelHandler();
            promise.trySuccess(response.bndAddrType() == Socks5AddressType.DOMAIN
                    ? new DomainNetAddr(response.bndAddr(), response.bndPort())
                            : NetAddr.of(response.bndAddr(), response.bndPort()));
        }
    }

    public Future<NetAddr> handshake(){
        if (start){
            return promise;
        }
        this.channel.writeAndFlush(new DefaultSocks5InitialRequest(Socks5AuthMethod.NO_AUTH));
        start=true;
        return promise;
    }

}
