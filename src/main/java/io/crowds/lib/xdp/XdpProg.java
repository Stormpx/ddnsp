package io.crowds.lib.xdp;

import io.crowds.lib.xdp.ffi.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

public record XdpProg(XdpProgram prog, int ifindex) {

    public int fd(){
        return LibXdp.INSTANCE.xdp_program__fd(prog);
    }

    public BpfObject bpfObject(){
        return LibXdp.INSTANCE.xdp_program__bpf_obj(prog);
    }

    public BpfMap map(String name){
        return LibBpf.INSTANCE.bpf_object__find_map_by_name(bpfObject(), Arena.global().allocateFrom(name));
    }

    public int mapFd(String name){
        return LibBpf.INSTANCE.bpf_object__find_map_fd_by_name(bpfObject(), Arena.global().allocateFrom(name));
    }

    public boolean isAttached(){
        return mode()!=0;
    }

    public void attach(int mode){
        if (isAttached()){
            throw new IllegalStateException("Program already attached");
        }
        LibXdp libXdp = LibXdp.INSTANCE;
        int err = libXdp.xdp_program__attach(prog,ifindex,mode,0);
        if (err!=0){
            MemorySegment errMsg = Arena.ofAuto().allocate(512);
            libXdp.libxdp_strerror(err,errMsg);
            throw new RuntimeException("Cloud not attach to iface : "+errMsg.getString(0));
        }
    }

    public void detach(){
        if (!isAttached()){
            throw new IllegalStateException("Program not attached");
        }
        LibXdp libXdp = LibXdp.INSTANCE;
        int err = libXdp.xdp_program__detach(prog,ifindex,mode(),0);
        if (err!=0){
            MemorySegment errMsg = Arena.ofAuto().allocate(512);
            libXdp.libxdp_strerror(err,errMsg);
            throw new RuntimeException("Could not detach xdp prog from iface: "+errMsg.getString(0));
        }
    }


    public int mode(){
        return LibXdp.INSTANCE.xdp_program__is_attached(prog,ifindex);
    }

}
