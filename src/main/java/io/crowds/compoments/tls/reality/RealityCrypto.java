package io.crowds.compoments.tls.reality;

import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.tls.AlertDescription;
import org.bouncycastle.tls.CertificateType;
import org.bouncycastle.tls.NamedGroup;
import org.bouncycastle.tls.TlsFatalAlert;
import org.bouncycastle.tls.crypto.TlsAgreement;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.tls.crypto.TlsECConfig;
import org.bouncycastle.tls.crypto.TlsECDomain;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;

import java.io.IOException;

/**
 * Crypto provider that keeps a handle on the X25519 ephemeral key share so the REALITY key can be
 * derived before the ClientHello is written, and that tolerates the REALITY server certificate.
 */
final class RealityCrypto extends BcTlsCrypto {

    private volatile RealityX25519 x25519;

    @Override
    public TlsECDomain createECDomain(TlsECConfig ecConfig) {
        if (ecConfig.getNamedGroup() != NamedGroup.x25519) {
            return super.createECDomain(ecConfig);
        }
        return () -> {
            RealityX25519 agreement = new RealityX25519(RealityCrypto.this);
            x25519 = agreement;
            return agreement;
        };
    }

    @Override
    public TlsCertificate createCertificate(short type, byte[] encoding) throws IOException {
        if (type != CertificateType.X509) {
            return super.createCertificate(type, encoding);
        }
        SubjectPublicKeyInfo subjectPublicKeyInfo = RealityAuth.subjectPublicKeyInfo(encoding);
        if (subjectPublicKeyInfo == null) {
            throw new TlsFatalAlert(AlertDescription.bad_certificate, "unexpected REALITY server certificate");
        }
        return new RealityTlsCertificate(this, encoding, subjectPublicKeyInfo);
    }

    RealityX25519 x25519() {
        return x25519;
    }
}
