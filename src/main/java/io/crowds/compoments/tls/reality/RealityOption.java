package io.crowds.compoments.tls.reality;

/**
 * REALITY transport settings. The client authenticates with an X25519 handshake against the
 * server's static public key, so no certificate chain validation is involved.
 */
public class RealityOption {

    public static final String DEFAULT_CLIENT_VERSION = "26.11.9";

    private byte[] publicKey;
    private byte[] shortId = new byte[8];
    private String clientVersion = DEFAULT_CLIENT_VERSION;
    private String spiderX;
    private byte[] mldsa65Verify;

    public RealityOption() {
    }

    public RealityOption(RealityOption other) {
        this.publicKey = other.publicKey == null ? null : other.publicKey.clone();
        this.shortId = other.shortId == null ? new byte[8] : other.shortId.clone();
        this.clientVersion = other.clientVersion;
        this.spiderX = other.spiderX;
        this.mldsa65Verify = other.mldsa65Verify == null ? null : other.mldsa65Verify.clone();
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public RealityOption setPublicKey(byte[] publicKey) {
        this.publicKey = publicKey;
        return this;
    }

    public byte[] getShortId() {
        return shortId;
    }

    public RealityOption setShortId(byte[] shortId) {
        if (shortId == null) {
            this.shortId = new byte[8];
        } else if (shortId.length > 8) {
            throw new IllegalArgumentException("reality shortId must not be longer than 8 bytes");
        } else {
            byte[] padded = new byte[8];
            System.arraycopy(shortId, 0, padded, 0, shortId.length);
            this.shortId = padded;
        }
        return this;
    }

    public String getClientVersion() {
        return clientVersion;
    }

    public RealityOption setClientVersion(String clientVersion) {
        this.clientVersion = clientVersion;
        return this;
    }

    public String getSpiderX() {
        return spiderX;
    }

    public RealityOption setSpiderX(String spiderX) {
        this.spiderX = spiderX;
        return this;
    }

    public byte[] getMldsa65Verify() {
        return mldsa65Verify;
    }

    public RealityOption setMldsa65Verify(byte[] mldsa65Verify) {
        this.mldsa65Verify = mldsa65Verify;
        return this;
    }

    public void validate() {
        if (publicKey == null || publicKey.length != 32) {
            throw new IllegalArgumentException("reality publicKey must be a 32 bytes X25519 public key");
        }
    }
}
