package io.crowds.dns;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class RR {

    private InetAddress address;


    public RR(String ip) throws UnknownHostException {
        this.address=InetAddress.getByName(ip);

    }

    public InetAddress getAddress() {
        return address;
    }

    @Override
    public String toString() {
        return address.toString();
    }
}
