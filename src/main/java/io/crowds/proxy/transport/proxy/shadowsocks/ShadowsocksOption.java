package io.crowds.proxy.transport.proxy.shadowsocks;

import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.util.Hash;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import java.net.InetSocketAddress;
import java.util.Base64;

public class ShadowsocksOption extends ProtocolOption {
    private InetSocketAddress address;
    private CipherAlgo cipherAlgo;
    private String password;
    private byte[] masterKey;

    public ShadowsocksOption() {
    }

    public ShadowsocksOption(ShadowsocksOption other) {
        super(other);
        this.address =other.address;
        this.cipherAlgo = other.cipherAlgo;
        this.password = other.password;
        this.masterKey = other.masterKey;
    }

    private void genMasterKey(){
        if (this.password==null||this.cipherAlgo ==null)
            return;
        switch (cipherAlgo){
            case CHACHA20_IETF_POLY1305,AES_128_GCM,AES_192_GCM,AES_256_GCM ->{
                byte[] key=new byte[cipherAlgo.getKeySize()];
                ByteBuf buf = Unpooled.buffer();

                int writerIndex=0;
                do {
                    buf.writerIndex(writerIndex).writeBytes(password.getBytes());
                    byte[] md5 = Hash.md5(ByteBufUtil.getBytes(buf));
                    buf.writerIndex(writerIndex).writeBytes(md5);
                    writerIndex+=md5.length;
                }while (buf.readableBytes()<key.length);

                buf.readBytes(key);
                this.masterKey=key;
            }
            case AES_128_GCM_2022,AES_256_GCM_2022 -> {
                this.masterKey=Base64.getDecoder().decode(password);
            }
        }

    }

    public byte[] getMasterKey(){
        return this.masterKey;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public ShadowsocksOption setAddress(InetSocketAddress address) {
        this.address = address;
        return this;
    }

    public CipherAlgo getCipher() {
        return cipherAlgo;
    }

    public ShadowsocksOption setCipher(CipherAlgo cipherAlgo) {
        this.cipherAlgo = cipherAlgo;
        genMasterKey();
        return this;
    }

    public String getPassword() {
        return password;
    }

    public ShadowsocksOption setPassword(String password) {
        this.password = password;
        genMasterKey();
        return this;
    }

}
