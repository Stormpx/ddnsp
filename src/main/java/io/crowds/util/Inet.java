package io.crowds.util;

import io.netty.buffer.ByteBufUtil;
import io.netty.util.NetUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public class Inet {

    /**
     *
     * @param host ip or hostname
     * @param port port
     * @return if host is an ip. return resolved InetSocketAddress
     */
    public static InetSocketAddress createSocketAddress(String host, int port){
        InetAddress inetAddress = NetUtil.createInetAddressFromIpAddressString(host);
        if (inetAddress!=null){
            return new InetSocketAddress(inetAddress, port);
        }else{
            return InetSocketAddress.createUnresolved(host,port);
        }
    }

    public static InetAddress address(byte[] addr){
        try {
            return InetAddress.getByAddress(addr);
        } catch (UnknownHostException e) {
            return null;
        }
    }

}
