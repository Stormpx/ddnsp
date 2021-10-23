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


    public boolean isOptionExists(Option option){
        if (opts==null){
            return false;
        }
        return opts.contains(option);
    }

    public byte[] getResponseKey() {
        if (this.responseKey==null){
            this.responseKey= Hash.md5(requestKey);
        }
        return responseKey;
    }

    public byte[] getResponseIv() {
        if (this.responseIv==null){
            this.responseIv = Hash.md5(requestIv);
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
