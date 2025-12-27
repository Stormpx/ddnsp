package io.crowds.proxy.transport.proxy.vless;


import io.netty.channel.*;

public class VlessTlsClamp  {

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

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            this.context = ctx;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (!skip){
                ctx.fireChannelRead(msg);
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
