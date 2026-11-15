package io.crowds.support;

import io.crowds.util.Rands;
import org.bouncycastle.math.ec.rfc7748.X25519;
import org.bouncycastle.util.encoders.Hex;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * X25519 key material shaped the way xray expects it: raw url base64 keys and a hex short id.
 */
public final class RealityKeys {

    private final byte[] privateKey;
    private final byte[] publicKey;
    private final byte[] shortId;

    private RealityKeys(byte[] privateKey, byte[] publicKey, byte[] shortId) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.shortId = shortId;
    }

    public static RealityKeys generate() {
        byte[] privateKey = new byte[32];
        X25519.generatePrivateKey(new SecureRandom(), privateKey);
        byte[] publicKey = new byte[32];
        X25519.generatePublicKey(privateKey, 0, publicKey, 0);
        return new RealityKeys(privateKey, publicKey, Rands.genBytes(8));
    }

    public static RealityKeys of(byte[] privateKey, byte[] shortId) {
        byte[] publicKey = new byte[32];
        X25519.generatePublicKey(privateKey, 0, publicKey, 0);
        return new RealityKeys(privateKey, publicKey, shortId);
    }

    public byte[] privateKey() {
        return privateKey;
    }

    public byte[] publicKey() {
        return publicKey;
    }

    public byte[] shortId() {
        return shortId;
    }

    public String privateKeyBase64() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(privateKey);
    }

    public String publicKeyBase64() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey);
    }

    public String shortIdHex() {
        return Hex.toHexString(shortId);
    }
}
