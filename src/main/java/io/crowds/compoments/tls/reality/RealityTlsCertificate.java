package io.crowds.compoments.tls.reality;

import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsRawKeyCertificate;

/**
 * Wraps the server certificate without running it through a validating X.509 parser, because the
 * REALITY certificate only carries a bare Ed25519 key and an HMAC signature.
 */
final class RealityTlsCertificate extends BcTlsRawKeyCertificate {

    private final byte[] encoded;

    RealityTlsCertificate(BcTlsCrypto crypto, byte[] encoded, SubjectPublicKeyInfo subjectPublicKeyInfo) {
        super(crypto, subjectPublicKeyInfo);
        this.encoded = encoded;
    }

    @Override
    public byte[] getEncoded() {
        return encoded;
    }
}
