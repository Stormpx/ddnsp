package io.crowds.util;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public enum AddrType {

    IPV4,
    IPV6
    ;

    public static AddrType of(InetAddress inetAddress){
        if (inetAddress==null) {
            return null;
        }
        if (inetAddress instanceof Inet4Address){
            return IPV4;
        }
        if (inetAddress instanceof Inet6Address){
            return IPV6;
        }
        return null;
    }

    public static AddrType of(InetSocketAddress inetSocketAddress){

        return inetSocketAddress==null?null:of(inetSocketAddress.getAddress());
    }

}
