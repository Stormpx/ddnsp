package io.crowds.util;

import io.netty.util.NetUtil;
import io.netty.util.internal.PlatformDependent;

import java.net.*;
import java.util.Enumeration;

public class Inet {
    public final static Inet4Address ANY_ADDRESS_V4;
    public final static Inet6Address ANY_ADDRESS_V6;

    static {
         try {
             ANY_ADDRESS_V4 = (Inet4Address) Inet4Address.getByName("0.0.0.0");
             ANY_ADDRESS_V6 = (Inet6Address) Inet6Address.getByName("::");
        } catch (UnknownHostException e) {
             //should not happen
             throw new RuntimeException(e);
        }
    }

    private static Boolean ipv6 = null;

    private static boolean is0SupportsIpV6(){
        try {
            final Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
            while (e.hasMoreElements()) {
                for (InterfaceAddress interfaceAddress : e.nextElement().getInterfaceAddresses()) {
                    final InetAddress ip = interfaceAddress.getAddress();
                    if (ip.isLoopbackAddress() || ip instanceof Inet4Address) {
                        continue;
                    }
                    return true;
                }
            }
            return false;
        } catch (SocketException ex) {
            return false;
        }
    }
    public static boolean isSupportsIpV6(){
        if (ipv6!=null){
            return ipv6;
        }
        return ipv6=is0SupportsIpV6();
    }

    public static InetSocketAddress parseInetAddress(String hostAndIp){
        try {
            String host=null;
            if (hostAndIp.startsWith("[")){
                int index = hostAndIp.lastIndexOf("]");
                if (index==-1){
                    throw new IllegalArgumentException(hostAndIp+": Invalid host and ip");
                }
                if (index==hostAndIp.length()-1){
                    throw new IllegalArgumentException(hostAndIp+": Must have port parts");
                }
                host = hostAndIp.substring(1, index);
                if (!NetUtil.isValidIpV6Address(host)){
                    throw new IllegalArgumentException(hostAndIp+": Invalid ipv6 address");
                }
            }
            int index = hostAndIp.lastIndexOf(":");
            if (index==-1){
                throw new IllegalArgumentException(hostAndIp+": Must have port parts");
            }
            if (host==null){
                host = hostAndIp.substring(0,index);
            }
            String portStr = hostAndIp.substring(index+1);
            return createSocketAddress(host,Integer.parseInt(portStr));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("%s: Invalid host and ip".formatted(hostAndIp));
        } catch (Exception e){
            throw new IllegalArgumentException("%s: %s".formatted(hostAndIp,e.getMessage()));
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

    private static NetworkInterface findByName(String name) throws SocketException {
        if (PlatformDependent.isWindows()){
            return NetworkInterface.networkInterfaces().filter(it->it.getDisplayName().equalsIgnoreCase(name)).findFirst().orElse(null);
        }else{
            return NetworkInterface.getByName(name);
        }
    }

    public static InetAddress getDeviceAddress(String dev, boolean ipv6){
        try {
            NetworkInterface networkInterface = findByName(dev);
            if (networkInterface==null){
                throw new SocketException("Network interface is not exists : "+dev);
            }
            if (!networkInterface.isUp()){
                throw new SocketException("Network interface is not up: "+dev);
            }
            return networkInterface.inetAddresses()
                    .filter(it->ipv6?it instanceof Inet6Address:it instanceof Inet4Address)
                    .findFirst()
                    .orElse(null);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

}
