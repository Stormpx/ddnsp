package io.crowds.proxy.transport.proxy.vless;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VlessTlsClamp  {

    private static final Logger logger = LoggerFactory.getLogger(VlessTlsClamp.class);
    private final VlessTlsClampRead read = new VlessTlsClampRead();
    private final VlessTlsClampWrite write = new VlessTlsClampWrite();

    public static VlessTlsClamp clamp(ChannelPipeline pipeline, String handlerName){

        VlessTlsClamp tlsClamp = new VlessTlsClamp();
        pipeline.addBefore(handlerName,"clamp-"+handlerName+"-read", tlsClamp.read);
        pipeline.addAfter(handlerName,"clamp-"+handlerName+"-write", tlsClamp.write);
        return tlsClamp;
    }

    public VlessTlsClampRead getRead() {
        return read;
    }

    public VlessTlsClampWrite getWrite() {
        return write;
    }


    public boolean isSkipRead() {
        return read.skip;
    }

    public VlessTlsClamp setSkipRead(boolean skipChannelRead) {
        read.skip = skipChannelRead;
        return this;
    }

    public boolean isSkipWrite() {
        return write.skip;
    }

    public VlessTlsClamp setSkipWrite(boolean skipChannelWrite) {
        write.skip = skipChannelWrite;
        return this;
    }

    public class VlessTlsClampRead extends ChannelDuplexHandler {

        public ChannelHandlerContext context;
        private boolean skip;

        private final ByteToMessageDecoder.Cumulator cumulator = ByteToMessageDecoder.MERGE_CUMULATOR;
        private ByteBuf cumulation = Unpooled.EMPTY_BUFFER;

        private int tlsCipherTextLen = -1;

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            this.context = ctx;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
//            logger.info("channelRead: skip={}, msg={}", skip, msg);
            boolean skip = this.skip;
            if (!skip){
                assert msg instanceof ByteBuf;
                this.cumulation = cumulator.cumulate(ctx.alloc(), cumulation, (ByteBuf) msg);
                while (cumulation.isReadable() && !skip){
                    if (tlsCipherTextLen==-1){
                        if (cumulation.readableBytes()<5){
                            return;
                        }
                        tlsCipherTextLen = 5 + ((cumulation.getUnsignedShort(cumulation.readerIndex() + 3) & 0xFFFF));
                    }
                    if (cumulation.readableBytes() < tlsCipherTextLen){
                        return;
                    }
                    //make sure we read a full tls record
                    ByteBuf slice = cumulation.readRetainedSlice(tlsCipherTextLen);
                    tlsCipherTextLen = -1;
                    ctx.fireChannelRead(slice);
                    skip = this.skip;
                }
                if (skip){
                    if (cumulation.isReadable()){
                        write.context.fireChannelRead(cumulation);
                    }
                    cumulation = null;
                }
            }else {
                write.context.fireChannelRead(msg);
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            if (!skip){
                ctx.fireChannelReadComplete();
            }else {
                write.context.fireChannelReadComplete();
            }
        }

    }

    public class VlessTlsClampWrite extends ChannelDuplexHandler {

        public ChannelHandlerContext context;
        private boolean skip;

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            this.context = ctx;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (!skip){
                ctx.write(msg,promise);
            }else {
                read.context.write(msg,promise);
            }
        }

        @Override
        public void flush(ChannelHandlerContext ctx) throws Exception {
            if (!skip){
                ctx.flush();
            }else {
                read.context.flush();
            }
        }

    }
}
