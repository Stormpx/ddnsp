package io.crowds.compoments.tls.reality;

import org.bouncycastle.math.ec.rfc7748.X25519;
import org.bouncycastle.tls.AlertDescription;
import org.bouncycastle.tls.TlsFatalAlert;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;
import org.bouncycastle.tls.crypto.impl.bc.BcX25519;

import java.io.IOException;
import java.util.Arrays;

/**
 * X25519 agreement that also exposes the ephemeral private key, which REALITY needs to derive the
 * authentication key independently of the TLS key schedule.
 */
final class RealityX25519 extends BcX25519 {

    private final byte[] authPrivateKey = new byte[X25519.SCALAR_SIZE];

    RealityX25519(BcTlsCrypto crypto) {
        super(crypto);
    }

    @Override
    public byte[] generateEphemeral() throws IOException {
        byte[] publicKey = super.generateEphemeral();
        System.arraycopy(privateKey, 0, authPrivateKey, 0, authPrivateKey.length);
        return publicKey;
    }

    byte[] agreement(byte[] peerPublicKey) throws IOException {
        byte[] secret = new byte[X25519.POINT_SIZE];
        if (!X25519.calculateAgreement(authPrivateKey, 0, peerPublicKey, 0, secret, 0)) {
            throw new TlsFatalAlert(AlertDescription.handshake_failure, "invalid REALITY public key");
        }
        return secret;
    }

    void destroy() {
        Arrays.fill(authPrivateKey, (byte) 0);
    }
}
