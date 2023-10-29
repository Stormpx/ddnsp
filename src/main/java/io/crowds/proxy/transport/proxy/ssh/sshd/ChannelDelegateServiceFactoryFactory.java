package io.crowds.proxy.transport.proxy.ssh.sshd;

import org.apache.sshd.common.FactoryManager;
import org.apache.sshd.common.io.AbstractIoServiceFactoryFactory;
import org.apache.sshd.common.io.IoServiceFactory;

public class ChannelDelegateServiceFactoryFactory extends AbstractIoServiceFactoryFactory {

    public ChannelDelegateServiceFactoryFactory() {
        super(null);
    }

    @Override
    public IoServiceFactory create(FactoryManager manager) {
        ChannelDelegateServiceFactory repository = new ChannelDelegateServiceFactory();
        repository.setIoServiceEventListener(manager==null?null:manager.getIoServiceEventListener());
        return repository;
    }



}
