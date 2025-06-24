package io.crowds.compoments.dns;

import io.crowds.util.AddrType;
import io.vertx.core.Future;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class FallbackDnsResolver implements InternalDnsResolver{


    @Override
    public Future<List<InetAddress>> bootResolveAll(String host, AddrType addrType) {
        return resolveAll(host, addrType);
    }

    @Override
    public Future<InetAddress> bootResolve(String host, AddrType addrType) {
        return resolve(host,addrType);
    }

    @Override
    public Future<List<InetAddress>> resolveAll(String host, AddrType addrType) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            List<InetAddress> result=new ArrayList<>();
            for (InetAddress address : addresses) {
                if (addrType==null||AddrType.of(address)==addrType){
                    result.add(address);
                }
            }
            if (result.isEmpty()){
                return Future.failedFuture(new UnknownHostException(host+": No matching address found"));
            }
            return Future.succeededFuture(result);
        } catch (UnknownHostException e) {
            return Future.failedFuture(e);
        }
    }

    @Override
    public Future<InetAddress> resolve(String host, AddrType addrType) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (addrType==null||AddrType.of(address)==addrType){
                    return Future.succeededFuture(address);
                }
            }
            return Future.failedFuture(new UnknownHostException(host+": No matching address found"));
        } catch (UnknownHostException e) {
            return Future.failedFuture(e);
        }
    }
}
