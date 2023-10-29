package io.crowds.proxy.transport.proxy.ssh;

import io.crowds.proxy.transport.ProtocolOption;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

public class SshOption extends ProtocolOption {

    private InetSocketAddress address;
    private String user;
    private String password;
    private Path privateKey;
    private String passphrase;

    private Path serverKey;

    private VerifyStrategy verifyStrategy;

    public enum VerifyStrategy{
        REJECT,
        REJECT_UNKNOWNS,
        ACCEPT_ONCE,
        ACCEPT_ALL,
        SPECIFIED
        ;

        public static VerifyStrategy valueOf0(String name){
            for (VerifyStrategy strategy : values()) {
                if (strategy.name().equalsIgnoreCase(name)){
                    return strategy;
                }
            }
            return null;
        }
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public SshOption setAddress(InetSocketAddress address) {
        this.address = address;
        return this;
    }


    public String getUser() {
        return user;
    }

    public SshOption setUser(String user) {
        this.user = user;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public SshOption setPassword(String password) {
        this.password = password;
        return this;
    }

    public Path getPrivateKey() {
        return privateKey;
    }

    public SshOption setPrivateKey(Path privateKey) {
        this.privateKey = privateKey;
        return this;
    }

    public String getPassphrase() {
        return passphrase;
    }

    public SshOption setPassphrase(String passphrase) {
        this.passphrase = passphrase;
        return this;
    }

    public Path getServerKey() {
        return serverKey;
    }

    public SshOption setServerKey(Path serverKey) {
        this.serverKey = serverKey;
        return this;
    }

    public VerifyStrategy getVerifyStrategy() {
        return verifyStrategy;
    }

    public SshOption setVerifyStrategy(VerifyStrategy verifyStrategy) {
        this.verifyStrategy = verifyStrategy;
        return this;
    }
}
