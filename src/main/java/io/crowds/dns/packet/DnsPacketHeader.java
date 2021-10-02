package io.crowds.dns.packet;

import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsResponseCode;

public class DnsPacketHeader {
    private short id;
    private boolean qr;
    private DnsOpCode opCode;
    private boolean aa;
    private boolean tc;
    private boolean rd;
    private boolean ra;
    private byte z;
    private DnsResponseCode rCode;
    private int qdCount;
    private int anCount;
    private int nsCount;
    private int arCount;



    public short getId() {
        return id;
    }

    public DnsPacketHeader setId(short id) {
        this.id = id;
        return this;
    }

    public DnsOpCode getOpCode() {
        return opCode;
    }

    public DnsPacketHeader setOpCode(DnsOpCode opCode) {
        this.opCode = opCode;
        return this;
    }

    public DnsPacketHeader setrCode(DnsResponseCode rCode) {
        this.rCode = rCode;
        return this;
    }

    public boolean isQr() {
        return qr;
    }

    public DnsPacketHeader setQr(boolean qr) {
        this.qr = qr;
        return this;
    }


    public boolean isAa() {
        return aa;
    }

    public DnsPacketHeader setAa(boolean aa) {
        this.aa = aa;
        return this;
    }

    public boolean isTc() {
        return tc;
    }

    public DnsPacketHeader setTc(boolean tc) {
        this.tc = tc;
        return this;
    }

    public boolean isRd() {
        return rd;
    }

    public DnsPacketHeader setRd(boolean rd) {
        this.rd = rd;
        return this;
    }

    public boolean isRa() {
        return ra;
    }

    public DnsPacketHeader setRa(boolean ra) {
        this.ra = ra;
        return this;
    }


    public int getQdCount() {
        return qdCount;
    }

    public DnsPacketHeader setQdCount(int qdCount) {
        this.qdCount = qdCount;
        return this;
    }

    public int getAnCount() {
        return anCount;
    }

    public DnsPacketHeader setAnCount(int anCount) {
        this.anCount = anCount;
        return this;
    }

    public int getNsCount() {
        return nsCount;
    }

    public DnsPacketHeader setNsCount(int nsCount) {
        this.nsCount = nsCount;
        return this;
    }

    public int getArCount() {
        return arCount;
    }

    public DnsPacketHeader setArCount(int arCount) {
        this.arCount = arCount;
        return this;
    }

    public byte getZ() {
        return z;
    }

    public DnsPacketHeader setZ(byte z) {
        this.z = z;
        return this;
    }
}
