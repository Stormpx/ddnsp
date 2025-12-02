package io.crowds.proxy.common;

import io.crowds.proxy.DomainNetAddr;
import io.crowds.proxy.NetAddr;
import io.crowds.util.IPCIDR;
import io.crowds.util.Inet;
import io.crowds.util.Ints;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class NatMap {

    private final List<NatEntry> entries = new ArrayList<>();

    sealed interface NatEntry  {

        record Domain(String domain,Translator translator) implements NatEntry{}
        record Cidr(IPCIDR cidr,Translator translator) implements NatEntry{}
    }

    static class Translator{
        private final String host;
        private final Integer port;
        private final NetAddr address;

        public Translator(String host, Integer port) {
            if (host==null&&port==null){
                throw new IllegalArgumentException("Host or port cannot be null");
            }
            if (host!=null&&port!=null){
                this.host = null;
                this.port = null;
                this.address = NetAddr.of(host,port);
            }else {
                this.host = host;
                this.port = port;
                this.address = null;
            }
        }

        public Translator(NetAddr address) {
            this.host = null;
            this.port = null;
            this.address = address;
        }

        NetAddr translate(NetAddr dest){
            if (address!=null){
                return address;
            }
            if (host!=null){
                return NetAddr.of(host,dest.getPort());
            }else{
                return NetAddr.of(dest.getHost(),port);
            }
        }
    }

    private Translator parse(String result){
        try {
            int port = Integer.parseInt(result);
            if (!Ints.isAvailablePort(port)){
                throw new IllegalArgumentException("Invalid port");
            }
            return new Translator(null,port);
        } catch (NumberFormatException e) {
            //ignore
        }

        try {
            InetSocketAddress inetSocketAddress = Inet.parseInetAddress(result);
            return new Translator(NetAddr.of(inetSocketAddress));
        } catch (Exception e) {
            //ignore
        }

        return new Translator(result,null);
    }

    public void add(String pattern,String result){
        Objects.requireNonNull(pattern);
        Objects.requireNonNull(result);

        Translator translator = parse(result);
        NatEntry entry = null;
        if (pattern.contains("/")){
            try {
                IPCIDR ipcidr = new IPCIDR(pattern);
                entry = new NatEntry.Cidr(ipcidr,translator);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid pattern",e);
            }
        }
        if (entry==null) {
            InetSocketAddress socketAddress = Inet.createSocketAddress(pattern, 0);
            if (socketAddress.isUnresolved()) {
                entry = new NatEntry.Domain(pattern, translator);
            } else {
                entry = new NatEntry.Cidr(new IPCIDR(socketAddress.getAddress(),32), translator);
            }
        }

        this.entries.add(entry);
    }


    public NetAddr translate(NetAddr dest){
        for (NatEntry entry : entries) {
            switch (entry){
                case NatEntry.Cidr cidr -> {
                    if (dest instanceof DomainNetAddr){
                        continue;
                    }
                    if (cidr.cidr.isMatch(dest.getAsInetAddr().getAddress().getAddress())){
                        return cidr.translator.translate(dest);
                    }
                }
                case NatEntry.Domain domain -> {
                    if (!(dest instanceof DomainNetAddr)){
                        continue;
                    }
                    if (domain.domain.equalsIgnoreCase(dest.getHost())){
                        return domain.translator.translate(dest);
                    }
                }
            }
        }
        return null;
    }

}
