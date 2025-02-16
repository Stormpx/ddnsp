package io.crowds.proxy.dns;

import io.crowds.util.IPCIDR;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.Queue;

public class IpPool {
    private IPCIDR ipcidr;
    private byte[] address;
    private byte[] prefixAddress;
    private byte seq = 0;
    private Queue<InetAddress> ipQueue;

    public IpPool(IPCIDR ipcidr) {
        this.ipcidr = ipcidr;
        this.prefixAddress=ipcidr.getPrefixBytes();
        reset(ipcidr);
    }

    public void reset(IPCIDR ipcidr){
        this.address=ipcidr.getFirstAddress();
        this.ipQueue=new LinkedList<>();
        int seqIndex = ipcidr.getMask() / 8;
        if (seqIndex<address.length)
            this.seq=this.address[seqIndex];
    }

    public boolean isMatch(InetAddress address){
        return this.ipcidr.isMatch(address.getAddress());
    }

    private byte[] nextAddress(){
        int seqIndex = ipcidr.getMask() / 8;
        for (int i = address.length-1; i > seqIndex-1; i--) {
            if (i==seqIndex){
                if (this.seq != (this.prefixAddress[seqIndex] & (address[i] + 1))){
                    return null;
                }
            }
            address[i]++;
            if (address[i]!=0){
                return address;
            }
        }
        return null;
    }

    public InetAddress getAvailableAddress(){

        try {
            if (!ipQueue.isEmpty()){
                return ipQueue.poll();
            }
            if (ipcidr.getMask()==ipcidr.getMaximumMask()){
                return null;
            }

            var next = nextAddress();
            if (next!=null){
                if (ipcidr.isBroadcastAddress(next)){
                    return null;
                }
                return InetAddress.getByAddress(next);
            }

            return null;

        } catch (UnknownHostException e) {
            throw new RuntimeException(e.getMessage());
        }

    }


    public void release(InetAddress address){
        if (isMatch(address)){
            ipQueue.add(address);
        }
    }




}
