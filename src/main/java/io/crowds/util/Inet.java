package io.crowds.util;

import io.netty.buffer.ByteBufUtil;
import io.netty.util.NetUtil;

import java.net.*;

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

    public static InetAddress getAddress(String dev,boolean ipv6){
        try {
            NetworkInterface networkInterface = NetworkInterface.getByName(dev);
            return networkInterface.inetAddresses()
                    .filter(it->ipv6?it instanceof Inet6Address:it instanceof Inet4Address)
                    .findFirst()
                    .orElse(null);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

}
