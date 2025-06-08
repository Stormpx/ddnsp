package io.crowds;

import io.netty.channel.*;
import io.netty.channel.local.LocalIoHandler;
import org.stormpx.net.PartialNetStack;
import org.stormpx.net.netty.PartialIoHandler;

public class DdnspIoHandler implements IoHandler {
    private final IoHandler localIoHandler;
    private final IoHandler partialIoHandler;

    public DdnspIoHandler(IoHandler localIoHandler, IoHandler partialIoHandler) {
        this.localIoHandler = localIoHandler;
        this.partialIoHandler = partialIoHandler;
    }

    public static IoHandlerFactory newFactory(PartialNetStack netStack,IoHandlerFactory ioHandlerFactory) {
        IoHandlerFactory localHandlerFactory = LocalIoHandler.newFactory();
        IoHandlerFactory partialIoHandlerFactory = PartialIoHandler.newFactory(netStack, ioHandlerFactory);
        return context-> new DdnspIoHandler(localHandlerFactory.newHandler(context), partialIoHandlerFactory.newHandler(context));
    }

    @Override
    public void initialize() {
        localIoHandler.initialize();
        partialIoHandler.initialize();
    }

    @Override
    public void prepareToDestroy() {
        localIoHandler.prepareToDestroy();
        partialIoHandler.prepareToDestroy();
    }

    @Override
    public void destroy() {
        localIoHandler.destroy();
        partialIoHandler.destroy();
    }

    @Override
    public int run(IoHandlerContext context) {
        return partialIoHandler.run(context);
    }

    @Override
    public IoRegistration register(IoHandle handle) throws Exception {
        if (localIoHandler.isCompatible(handle.getClass())){
            return localIoHandler.register(handle);
        }
        return partialIoHandler.register(handle);
    }

    @Override
    public void wakeup() {
        partialIoHandler.wakeup();
    }

    @Override
    public boolean isCompatible(Class<? extends IoHandle> handleType) {
        return partialIoHandler.isCompatible(handleType)|| localIoHandler.isCompatible(handleType);
    }
}
