package io.crowds.proxy.transport.proxy.ssh.sshd;

import io.netty.channel.Channel;
import org.apache.sshd.common.io.*;
import org.apache.sshd.common.util.closeable.AbstractCloseable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class ChannelDelegateServiceFactory extends AbstractCloseable implements IoServiceFactory {

    private IoServiceEventListener listener;

    private Set<Channel> heldChannels= ConcurrentHashMap.newKeySet();


    boolean acquire(Channel channel){
        boolean acquire = heldChannels.add(channel);
        if (acquire){
            channel.closeFuture().addListener(f->heldChannels.remove(channel));
        }
        return acquire;
    }

    @Override
    public IoConnector createConnector(IoHandler handler) {
        ChannelDelegateService service = new ChannelDelegateService(handler,this);
        service.setIoServiceEventListener(listener);
        return service;
    }

    @Override
    public IoAcceptor createAcceptor(IoHandler handler) {
        throw new UnsupportedOperationException("CreateAcceptor is not supported yet");
    }

    @Override
    public IoServiceEventListener getIoServiceEventListener() {
        return listener;
    }

    @Override
    public void setIoServiceEventListener(IoServiceEventListener listener) {
        this.listener=listener;
    }


}
