package io.crowds.proxy.transport.vmess;


import io.crowds.util.Hash;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class VmessSession {

    private VmessUser user;
    private long timestamp;
    private byte[] requestKey;
    private byte[] requestIv;
    private byte[] responseKey;
    private byte[] responseIv;
    private byte responseHeader;


    private Set<Option> opts;

    public VmessSession(VmessUser user, long timestamp, byte[] requestKey, byte[] requestIv, byte responseHeader) {
        this.user = user;
        this.timestamp = timestamp;
        this.requestKey = requestKey;
        this.requestIv = requestIv;
        this.responseHeader = responseHeader;
    }

    public static VmessSession create(VmessUser user, long timestamp){
        Objects.requireNonNull(user,"user is required");

        byte[] key=new byte[16];
        byte[] iv=new byte[16];
        byte[] rh=new byte[1];
        ThreadLocalRandom random = ThreadLocalRandom.current();
        random.nextBytes(key);
        random.nextBytes(iv);
        random.nextBytes(rh);

        return new VmessSession(user,timestamp,key,iv,rh[0]);

    }

    public boolean isAEAD(){
        return user.isPrimary();
    }

    public boolean isOptionExists(Option option){
        if (opts==null){
            return false;
        }
        return opts.contains(option);
    }

    public byte[] getResponseKey() {
        if (this.responseKey==null){
            if (!isAEAD()) {
                this.responseKey = Hash.md5(requestKey);
            }else{
                this.responseKey=new byte[16];
                byte[] sha256 = Hash.sha256(requestKey);
                System.arraycopy(sha256,0,this.responseKey,0,this.responseKey.length);
            }
        }
        return responseKey;
    }

    public byte[] getResponseIv() {
        if (this.responseIv==null){
            if (!isAEAD()){
                this.responseIv = Hash.md5(requestIv);
            }else{
                this.responseIv=new byte[16];
                byte[] sha256 = Hash.sha256(requestIv);
                System.arraycopy(sha256,0,this.responseIv,0,this.responseIv.length);
            }
        }
        return responseIv;
    }

    public VmessSession setOpts(Set<Option> opts) {
        this.opts = opts;
        return this;
    }

    public VmessUser getUser() {
        return user;
    }

    public byte[] getRequestKey() {
        return requestKey;
    }

    public byte[] getRequestIv() {
        return requestIv;
    }

    public byte getResponseHeader() {
        return responseHeader;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public VmessSession setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        return this;
    }
}
