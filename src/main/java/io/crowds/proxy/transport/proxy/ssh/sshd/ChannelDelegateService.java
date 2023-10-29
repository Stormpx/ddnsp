package io.crowds.proxy.transport.proxy.ssh.sshd;

import io.netty.channel.Channel;
import org.apache.sshd.common.AttributeRepository;
import org.apache.sshd.common.io.*;
import org.apache.sshd.common.util.closeable.AbstractCloseable;

import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ChannelDelegateService extends AbstractCloseable implements IoConnector {
    public static final AttributeRepository.AttributeKey<Channel> CHANNEL = new AttributeRepository.AttributeKey<>();
    private final AtomicLong id=new AtomicLong(0);
    private final Map<Long,IoSession> ioSessions=new ConcurrentHashMap<>();

    private final IoHandler ioHandler;
    private IoServiceEventListener ioServiceEventListener;

    private final ChannelDelegateServiceFactory channelDelegateServiceFactory;

    public ChannelDelegateService(IoHandler ioHandler, ChannelDelegateServiceFactory serviceFactory) {
        this.ioHandler = ioHandler;
        this.channelDelegateServiceFactory=serviceFactory;
    }

    @Override
    public Map<Long, IoSession> getManagedSessions() {
        return ioSessions;
    }

    @Override
    public IoServiceEventListener getIoServiceEventListener() {
        return ioServiceEventListener;
    }

    @Override
    public void setIoServiceEventListener(IoServiceEventListener listener) {
        this.ioServiceEventListener=listener;
    }

    @Override
    public IoConnectFuture connect(SocketAddress targetAddress, AttributeRepository context, SocketAddress localAddress) {
        Channel channel = context.getAttribute(CHANNEL);
        if (channel==null){
            DefaultIoConnectFuture future = new DefaultIoConnectFuture(targetAddress, null);
            future.setException(new IllegalArgumentException("unable create SSH IoSession, because Channel not found"));
            return future;
        }
        if (!channelDelegateServiceFactory.acquire(channel)) {
            DefaultIoConnectFuture future = new DefaultIoConnectFuture(targetAddress, null);
            future.setException(new IllegalArgumentException("unable create SSH IoSession, because Channel has been delegate by other IoSession"));
            return future;
        }
        long channelId = id.getAndIncrement();
        ChannelDelegateIoSession ioSession = new ChannelDelegateIoSession(ioHandler,this,channelId, channel);
        return ioSession.getConnectFuture();
    }
}
