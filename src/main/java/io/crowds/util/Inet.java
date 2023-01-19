package io.crowds.util;

import io.netty.util.NetUtil;

import java.io.StringReader;
import java.net.*;

public class Inet {

    public static InetSocketAddress parseInetAddress(String hostAndIp){
        try {
            String host=null;
            if (hostAndIp.startsWith("[")){
                int index = hostAndIp.lastIndexOf("]");
                if (index==-1){
                    throw new IllegalArgumentException("invalid host and ip");
                }
                if (index==hostAndIp.length()-1){
                    throw new IllegalArgumentException("must have port parts");
                }
                host = hostAndIp.substring(1, index);
                if (!NetUtil.isValidIpV6Address(host)){
                    throw new IllegalArgumentException("invalid ipv6 address");
                }
            }
            int index = hostAndIp.lastIndexOf(":");
            if (index==-1){
                throw new IllegalArgumentException("must have port parts");
            }
            if (host==null){
                host = hostAndIp.substring(0,index);
            }
            String portStr = hostAndIp.substring(index+1);
            return createSocketAddress(host,Integer.parseInt(portStr));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("parse %s failed cause:invalid host and ip".formatted(hostAndIp));
        } catch (Exception e){
            throw new IllegalArgumentException("parse %s failed cause:%s".formatted(hostAndIp,e.getMessage()));
        }

    }

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

    public static InetAddress getDeviceAddress(String dev, boolean ipv6){
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
