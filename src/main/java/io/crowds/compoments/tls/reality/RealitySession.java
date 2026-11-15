package io.crowds.compoments.tls.reality;

/**
 * State shared by the TLS client, the protocol instance and the Netty handler of one connection.
 */
final class RealitySession {

    private final RealityOption option;
    private final RealityCrypto crypto = new RealityCrypto();
    private byte[] authKey;
    private boolean authenticated;

    RealitySession(RealityOption option) {
        this.option = option;
    }

    String clientVersion(){
        return option.getClientVersion();
    }

    byte[] publicKey(){
        return option.getPublicKey();
    }

    byte[] shortId(){
        return option.getShortId();
    }

    RealityCrypto crypto() {
        return crypto;
    }

    byte[] authKey() {
        return authKey;
    }

    void authKey(byte[] authKey) {
        this.authKey = authKey;
    }

    boolean isAuthenticated() {
        return authenticated;
    }

    void authenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }
}
