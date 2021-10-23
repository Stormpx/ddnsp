package io.crowds.proxy.transport.vmess;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.TP;

import java.util.Set;


public class VmessRequest {
    private VmessUser user;
    private Set<Option> opts;
    private Security security=Security.NONE;
    private TP cmd;
    private NetAddr addr;

    public VmessRequest(Set<Option> opts, TP cmd, NetAddr addr) {
        this.opts = opts;
        this.cmd = cmd;
        this.addr = addr;
    }

    public VmessUser getUser() {
        return user;
    }

    public VmessRequest setUser(VmessUser user) {
        this.user = user;
        return this;
    }

    public Set<Option> getOpts() {
        return opts;
    }

    public VmessRequest setOpts(Set<Option> opts) {
        this.opts = opts;
        return this;
    }

    public Security getSecurity() {
        return security;
    }

    public VmessRequest setSecurity(Security security) {
        this.security = security;
        return this;
    }

    public TP getCmd() {
        return cmd;
    }

    public VmessRequest setCmd(TP cmd) {
        this.cmd = cmd;
        return this;
    }

    public NetAddr getAddr() {
        return addr;
    }

    public VmessRequest setAddr(NetAddr addr) {
        this.addr = addr;
        return this;
    }
}
