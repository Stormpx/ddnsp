package io.crowds.util;

import io.netty.util.NetUtil;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Objects;

public class IPCIDR {

    private InetAddress address;
    private byte[] addressBytes;
    private byte[] prefixBytes;
    private int mask;

    public IPCIDR(String cidr) {
        Objects.requireNonNull(cidr,"cidr");
        String[] strings = cidr.split("/");
        if (strings.length!=2){
            throw new IllegalArgumentException("invalid cidr "+cidr);
        }
        this.address=NetUtil.createInetAddressFromIpAddressString(strings[0]);
        if (this.address==null){
            throw new IllegalArgumentException("invalid host "+strings[0]);
        }
        this.addressBytes =this.address.getAddress();
        this.mask=Integer.parseInt(strings[1]);
        if (this.mask<0||this.mask> addressBytes.length*8){
            throw new IllegalArgumentException("invalid mask");
        }
        this.prefixBytes=new byte[addressBytes.length];
        int i = this.mask / 8;
        Arrays.fill(this.prefixBytes,0,i, (byte) 0xff);
        if (i <this.addressBytes.length&&this.mask%8!=0){
            int j = -256 >> (this.mask % 8);
            this.prefixBytes[i]= (byte) (0xff&j);
            this.addressBytes[i]= (byte) (this.addressBytes[i]& j);
            Arrays.fill(this.addressBytes,i+1,this.addressBytes.length, (byte) 0);
        }else {
            Arrays.fill(this.addressBytes, i, this.addressBytes.length, (byte) 0);
        }
    }


    public boolean isMatch(byte[] bytes){
        if (this.mask==0)
            return true;
        if (bytes.length!=this.addressBytes.length)
            return false;

        for (int i = 0; i < this.addressBytes.length; i++) {
            if ((this.prefixBytes[i]&bytes[i])!=this.addressBytes[i]){
                return false;
            }
        }
        return true;
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getMask() {
        return mask;
    }

    public int getMaximumMask(){
        return addressBytes.length*8;
    }

    public byte[] getFirstAddress(){
        return Arrays.copyOf(this.addressBytes,this.addressBytes.length);
    }

    public byte[] getPrefixBytes() {
        return Arrays.copyOf(this.prefixBytes,this.prefixBytes.length);
    }


    @Override
    public String toString() {
        return this.address.getHostAddress()+"/"+this.mask;
    }
}
