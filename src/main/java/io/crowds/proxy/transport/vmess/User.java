package io.crowds.proxy.transport.vmess;

import io.crowds.util.Hash;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class User {

    private int alterId;
    private VmessUser primaryUser;
    private VmessUser[] alterUser;

    public User(UUID uuid, int alterId) {
        this.alterId = alterId;
        this.primaryUser=newId(uuid);
        genUser();
    }

    private VmessUser newId(UUID uuid){
        VmessUser user = new VmessUser(uuid, false);
        byte[] uuidByte= ByteBufUtil.getBytes(Unpooled.buffer(16).writeLong(uuid.getMostSignificantBits()).writeLong(uuid.getLeastSignificantBits())
                .writeBytes("c48619fe-8f02-49e0-b9e9-edf763e17e21".getBytes(StandardCharsets.UTF_8)));
        byte[] idHasH= Hash.md5(uuidByte);
        user.setCmdKey(idHasH);
        return user;
    }

    private UUID nextId(UUID uuid){
        ByteBuf buf = Unpooled.buffer(16);
        byte[] bytes= ByteBufUtil.getBytes(buf.writeLong(uuid.getMostSignificantBits()).writeLong(uuid.getLeastSignificantBits())
                .writeBytes("16167dc8-16b6-4e6d-b8bb-65dd68113a81".getBytes(StandardCharsets.UTF_8)));



        UUID newUuid = createId(bytes);
        while (uuid.equals(newUuid)){
            bytes=ByteBufUtil.getBytes(buf.writeBytes("533eff8a-4113-4b10-b5ce-0f5d76b98cd2".getBytes(StandardCharsets.UTF_8)));
            newUuid = createId(bytes);
        }

        return newUuid;

    }

    private UUID createId(byte[] bytes){
        byte[] md5 = Hash.md5(bytes);
        ByteBuf buf = Unpooled.wrappedBuffer(md5);
        long most = buf.readLong();
        long least = buf.readLong();
        return new UUID(most,least);
    }


    private void genUser(){
        this.alterUser=new VmessUser[alterId];
        var prevId=primaryUser.getUuid();
        for (int i = 0; i < alterId; i++) {
            UUID uuid = nextId(prevId);
//            this.alterUser[i]=newId(uuid);
            this.alterUser[i]=new VmessUser(uuid, false).setCmdKey(primaryUser.getCmdKey());
            prevId=uuid;
        }

    }

    public VmessUser randomUser(){
        if (alterUser.length==0){
            return primaryUser;
        }
        return alterUser[ThreadLocalRandom.current().nextInt(0,alterId)];
    }

    public VmessUser[] getAlterUser() {
        return alterUser;
    }

    public VmessUser getPrimaryUser() {
        return primaryUser;
    }

    public int getAlterId() {
        return alterId;
    }

    public User setAlterId(int alterId) {
        this.alterId = alterId;
        return this;
    }
}
