package io.crowds.util;

import io.netty.util.NetUtil;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Objects;

public class IPCIDR {

    private InetAddress address;

    private final BigInteger netBits;
    private final BigInteger netInt;

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
        this.mask=Integer.parseInt(strings[1]);
        var addressBytes =this.address.getAddress();
        if (this.mask<0||this.mask> addressBytes.length*8){
            throw new IllegalArgumentException("invalid mask");
        }

        if (mask==0){
            this.netBits = BigInteger.ZERO;
            this.netInt = new BigInteger(addressBytes);
        }else{
            int bitLength = addressBytes.length * 8;
            var allOne = BigInteger.ONE.shiftLeft(bitLength).subtract(BigInteger.ONE);
            var idBits = BigInteger.ONE.shiftLeft(bitLength-mask).subtract(BigInteger.ONE);
            this.netBits = allOne.subtract(idBits);
//            System.out.println(new BigInteger(addressBytes));
            this.netInt = new BigInteger(addressBytes).and(netBits);
        }
    }


    public boolean isMatch(byte[] addr){
        return switch (mask) {
            case 0 -> true;
            default -> {
                BigInteger bigInteger = new BigInteger(addr);
                yield netInt.compareTo(bigInteger.and(this.netBits)) == 0;
            }
        };
    }

    public boolean isBroadcastAddress(byte[] addr){
        return new BigInteger(addr).andNot(netBits).bitCount() == mask;
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getMask() {
        return mask;
    }

    public int getMaximumMask(){
        return (this.address instanceof Inet4Address?4:16)*8;
    }

    public byte[] getFirstAddress(){
        var address = new byte[this.address instanceof Inet4Address?4:16];
        byte[] byteArray = this.netInt.toByteArray();
        System.arraycopy(byteArray,byteArray.length==address.length?0:1,address,0,address.length);
        return address;
    }

    public byte[] getPrefixBytes() {
        var prefix = new byte[this.address instanceof Inet4Address?4:16];
        byte[] byteArray = this.netBits.toByteArray();
        System.arraycopy(byteArray,byteArray.length==prefix.length?0:1,prefix,0,prefix.length);
        return prefix;
    }


    @Override
    public String toString() {
        return this.address.getHostAddress()+"/"+this.mask;
    }
}
