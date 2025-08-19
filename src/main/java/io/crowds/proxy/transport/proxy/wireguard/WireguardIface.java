package io.crowds.proxy.transport.proxy.wireguard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stormpx.net.buffer.ByteArray;
import org.stormpx.net.network.Iface;
import org.stormpx.net.network.IfaceIngress;
import org.stormpx.net.network.NetworkParams;

import java.util.function.Consumer;

public class WireguardIface implements Iface {

    private static final Logger logger = LoggerFactory.getLogger(WireguardIface.class);
    private IfaceIngress ifaceIngress;
    private Consumer<ByteArray> packetHandler;


    public WireguardIface packetHandler(Consumer<ByteArray> packetHandler) {
        this.packetHandler = packetHandler;
        return this;
    }

    public boolean writeToStack(ByteArray byteArray){
        ifaceIngress.enqueue(byteArray);
        ifaceIngress.callback();
        return true;
    }

    public void triggerStackReceive(){
        ifaceIngress.callback();
    }


    @Override
    public void init(NetworkParams networkParams, IfaceIngress ifaceIngress) {
        this.ifaceIngress = ifaceIngress;
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
