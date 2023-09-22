package io.crowds.ddns;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Iterator;
import java.util.Objects;

public class InterfaceIpProvider implements IpProvider {

    private String interfaceName;

    public InterfaceIpProvider(JsonObject option){
        this(option.getString("interface"));
    }

    public InterfaceIpProvider(String interfaceName) {
        Objects.requireNonNull(interfaceName);
        this.interfaceName = interfaceName;
    }

    @Override
    public Future<String> getIpv4() {
        try {

            Iterator<NetworkInterface> iterator = NetworkInterface.getNetworkInterfaces().asIterator();
            while (iterator.hasNext()){
                NetworkInterface netIf = iterator.next();
                if (this.interfaceName.equals(netIf.getDisplayName())||this.interfaceName.equals(netIf.getName())){
                    var address=netIf.inetAddresses()
                            .filter(it->it instanceof Inet4Address)
                            .findFirst()
                            .orElse(null);
                    if (address!=null) {
                        return Future.succeededFuture(address.getHostAddress());
                    }
                }
            }
            return Future.failedFuture("ipv4 not found");
        } catch (SocketException e) {
            return Future.failedFuture(e);
        }
    }

    @Override
    public Future<String> getIpv6() {
        try {

            Iterator<NetworkInterface> iterator = NetworkInterface.getNetworkInterfaces().asIterator();
            while (iterator.hasNext()){
                NetworkInterface netIf = iterator.next();
                if (this.interfaceName.equals(netIf.getDisplayName())||this.interfaceName.equals(netIf.getName())){
                    var address=netIf.inetAddresses()
                                     .filter(it->it instanceof Inet6Address)
                                     .findFirst()
                                     .orElse(null);
                    if (address!=null) {
                        return Future.succeededFuture(address.getHostAddress());
                    }
                }
            }
            return Future.failedFuture("ipv6 not found");
        } catch (SocketException e) {
            return Future.failedFuture(e);
        }
    }
}
