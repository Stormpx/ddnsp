package io.crowds.proxy.transport.proxy.vless;

import io.crowds.Ddnsp;
import io.crowds.compoments.tls.TlsUtils;
import io.crowds.proxy.TP;
import io.crowds.proxy.transport.Destination;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.crowds.proxy.transport.proxy.vless.Vless.*;

public class VlessHandler extends ChannelDuplexHandler {
    private static final Logger logger = LoggerFactory.getLogger(VlessHandler.class);

    private static final int NUMBER_OF_PACKET_TO_PADDING = 4;
    private static final int PADDING_CONTINUE = 0;
    private static final int END_PADDING = 1;
    private static final int PADDING_ENDED = 2;
    private final Vless.Flow flow;
    private final UUID id;
    private final Destination dest;

    private VlessRequest request;
    private VlessTlsClamp tlsClamp;
    private SslHandler tlsHandler;

    private int serverPaddingCounter = 0;
    private int clientPaddingCounter = 0;

    private int paddingState = PADDING_CONTINUE;
    private byte endPaddingCommand = COMMAND_PADDING_END;
    private boolean innerTls;
    private int innerServerHelloLen= -1 ;
    private int innerTlsCipher = -1;


    public VlessHandler(Vless.Flow flow,UUID id, Destination dest) {
        this.id = id;
        this.dest = dest;
        if (flow == Vless.Flow.XRV && dest.tp()==TP.UDP){
            flow = Vless.Flow.NONE;
        }
        VlessRequest request = new VlessRequest(id, dest);
        if (flow == Vless.Flow.XRV){
            request.setFlow(flow);
            request.setAddons(AddonsOuterClass.Addons.newBuilder().setFlow(flow.value()).build());
        }
        this.request = request;
        this.flow = flow;
    }

    private void checkOuterTlsV13(){
        if (this.tlsHandler!=null){
            if (!"TLSv1.3".equalsIgnoreCase(tlsHandler.engine().getSession().getProtocol())){
                logger.error("Vless XTLS require TLSv1.3, but got {}", tlsHandler.engine().getSession().getProtocol());
            }
        }
    }


    private void tryRemoveTlsHandlers(ChannelHandlerContext ctx){

        if (this.tlsClamp.isSkipRead() && this.tlsClamp.isSkipWrite()){
            ctx.executor().submit(()->{
                ctx.pipeline()
                   .remove(tlsHandler)
                   .remove(tlsClamp.getRead())
                   .remove(tlsClamp.getWrite());
                this.tlsHandler = null;
                this.tlsClamp = null;
            });
        }
    }

    private void skipTlsRead(ChannelHandlerContext ctx){
        if (this.tlsClamp!=null){
            //skip the tls read
            this.tlsClamp.setSkipRead(true);
            tryRemoveTlsHandlers(ctx);
        }
    }

    private void skipTlsWrite(ChannelHandlerContext ctx){
        if (this.tlsClamp!=null){
            this.tlsClamp.setSkipWrite(true);
            tryRemoveTlsHandlers(ctx);
        }
    }

    private void filterServerTls(ByteBuf data){
        if (innerServerHelloLen == -1){
            if (data.readableBytes()>=6){
                byte[] bytes = ByteBufUtil.getBytes(data, 0, TLS_SERVER_HAND_SHAKE_START.length);
                if (Arrays.equals(bytes, TLS_SERVER_HAND_SHAKE_START) && data.getByte(5) == Vless.TLS_HANDSHAKE_TYPE_SERVER_HELLO){
                    innerServerHelloLen = data.getShort(3) + 5;
                    innerTls = true;
                    if (data.readableBytes()>=79 && innerServerHelloLen >= 79){
                        int sessionIdLen = data.getByte(43);
                        innerTlsCipher = data.getShort(43+sessionIdLen+1);
                    }else{
                        logger.debug("XtlsFilterTls short server hello, tls 1.2 or older?");
                    }
                }
            }
        }
        if (innerServerHelloLen > 0){
            innerServerHelloLen -= data.readableBytes();
            if (ByteBufUtil.indexOf(Unpooled.wrappedBuffer(TLS_13_SUPPORTED_VERSIONS),data)!=-1){
                String cipher = TLS_13_CIPHER_SUITE_DIC.get(innerTlsCipher);
                if (cipher!=null && !cipher.equals("TLS_AES_128_CCM_8_SHA256")){
                    endPaddingCommand = COMMAND_PADDING_DIRECT;
                }
                logger.debug("XtlsFilterTls found tls 1.3!");
                if (paddingState==PADDING_CONTINUE){
                    paddingState = END_PADDING;
                }
            }else if (innerServerHelloLen <=0){
                logger.debug("XtlsFilterTls found tls 1.2!");
            }

        }
    }

    private boolean filterClientTls(ByteBuf data){
        if (data.readableBytes()>=6){
            byte[] bytes = ByteBufUtil.getBytes(data, 0, TLS_CLIENT_HAND_SHAKE_START.length);
            if (Arrays.equals(bytes, TLS_CLIENT_HAND_SHAKE_START) && data.getByte(5) == Vless.TLS_HANDSHAKE_TYPE_CLIENT_HELLO){
                innerTls = true;
                logger.debug("XtlsFilterTls found tls client hello!");
            }
        }
        return true;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        ctx.executor().schedule(()->{
            if (request!=null){
                ctx.write(request);
                if (flow==Flow.XRV){
                    ctx.write(new VisionData(COMMAND_PADDING_CONTINUE,Unpooled.EMPTY_BUFFER,true,true));
                }
                ctx.flush();
                request=null;
            }
        },50, TimeUnit.MILLISECONDS);

        if (flow == Vless.Flow.XRV){
            ChannelHandlerContext tlsHandlerContext = ctx.pipeline().context(SslHandler.class);
            assert tlsHandlerContext!=null;
            this.tlsClamp = VlessTlsClamp.clamp(ctx.pipeline(), tlsHandlerContext.name());
            this.tlsHandler = (SslHandler) tlsHandlerContext.handler();
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        switch (flow){
            case NONE -> super.channelRead(ctx, msg);
            case XRV -> {
                ByteBuf data;
                if (msg instanceof ByteBuf buf) {
                    data = buf;
                }else if (msg instanceof VisionData vision){
                    byte command = vision.command();
                    data = vision.data();

                    serverPaddingCounter++;

                    if (command==Vless.COMMAND_PADDING_DIRECT){
                        //we just let the server determine it is ok to remove the tls
                        this.innerTls = true;
                        this.endPaddingCommand = COMMAND_PADDING_DIRECT;
                        if (paddingState==PADDING_CONTINUE){
                            paddingState = END_PADDING;
                        }
                        skipTlsRead(ctx);
                    }else{
                        filterServerTls(data);
                    }

                }else{
                    throw new IllegalArgumentException("Unsupported payload type: "+msg.getClass());
                }
                ctx.fireChannelRead(data);
            }
        }

    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof SslHandshakeCompletionEvent event){
            if (event.isSuccess()){
                checkOuterTlsV13();
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (request!=null){
            ctx.write(request);
            request=null;
        }
        switch (flow){
            case NONE -> super.write(ctx, msg, promise);
            case XRV -> {
                if (!(msg instanceof ByteBuf data)){
                    throw new IllegalArgumentException("Unsupported payload type: "+msg.getClass());
                }
                if (paddingState< PADDING_ENDED){
                    if (clientPaddingCounter==0){
                        filterClientTls(data);
                    }

                    byte paddingCommand = Vless.COMMAND_PADDING_CONTINUE;
                    boolean longPadding = innerTls;

                    if (paddingState==END_PADDING){
                        paddingCommand = endPaddingCommand;
                        longPadding = true;
                        paddingState = PADDING_ENDED;
                    }else{
                        clientPaddingCounter++;
                        if (serverPaddingCounter>0 && serverPaddingCounter+clientPaddingCounter >= NUMBER_OF_PACKET_TO_PADDING){
                            paddingState = END_PADDING;
                        }
                    }

                    ctx.write(new VisionData(paddingCommand,data,true,longPadding),promise);

                    if (paddingCommand == COMMAND_PADDING_DIRECT){
                        ctx.flush();
                        skipTlsWrite(ctx);
                    }

                }else{
                    ctx.write(data,promise);
                }

            }
        }

    }


}
