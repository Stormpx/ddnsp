package io.crowds.proxy.transport.proxy.wireguard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stormpx.net.buffer.ByteArray;
import org.stormpx.net.network.Iface;
import org.stormpx.net.network.IfaceEntry;
import org.stormpx.net.network.NetworkParams;

import java.util.function.Consumer;

public class WireguardIface implements Iface {

    private static final Logger logger = LoggerFactory.getLogger(WireguardIface.class);
    private IfaceEntry ifaceEntry;
    private Consumer<ByteArray> packetHandler;


    public WireguardIface packetHandler(Consumer<ByteArray> packetHandler) {
        this.packetHandler = packetHandler;
        return this;
    }

    public boolean writeToStack(ByteArray byteArray){
        ifaceEntry.enqueue(byteArray);
        ifaceEntry.callback();
        return true;
    }

    public void triggerStackReceive(){
        ifaceEntry.callback();
    }


    @Override
    public void init(NetworkParams networkParams, IfaceEntry ifaceEntry) {
        this.ifaceEntry = ifaceEntry;
    }

    @Override
    public boolean transmit(ByteArray byteArray) {
        Consumer<ByteArray> packetHandler = this.packetHandler;
        if (packetHandler!=null){
            packetHandler.accept(byteArray);
            return true;
        }
        return false;
    }

    @Override
    public void destroy() {

    }


}
