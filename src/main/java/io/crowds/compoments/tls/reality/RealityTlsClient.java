package io.crowds.compoments.tls.reality;

import io.netty.util.NetUtil;
import org.bouncycastle.tls.AbstractTlsClient;
import org.bouncycastle.tls.AlertDescription;
import org.bouncycastle.tls.CertificateRequest;
import org.bouncycastle.tls.NameType;
import org.bouncycastle.tls.NamedGroup;
import org.bouncycastle.tls.ProtocolName;
import org.bouncycastle.tls.ProtocolVersion;
import org.bouncycastle.tls.ServerName;
import org.bouncycastle.tls.TlsAuthentication;
import org.bouncycastle.tls.TlsCredentials;
import org.bouncycastle.tls.TlsFatalAlert;
import org.bouncycastle.tls.TlsServerCertificate;
import org.bouncycastle.tls.TlsUtils;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.util.Integers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Vector;

/**
 * TLS 1.3 client restricted to the REALITY handshake profile: a single X25519 key share and a
 * certificate check based on the authentication key instead of a certificate chain.
 */
final class RealityTlsClient extends AbstractTlsClient implements TlsAuthentication {

    private final RealitySession session;
    private final String serverName;
    private final List<String> alpn;

    RealityTlsClient(RealitySession session, String serverName, List<String> alpn) {
        super(session.crypto());
        this.session = session;
        this.serverName = serverName;
        this.alpn = alpn;
    }

    @Override
    public ProtocolVersion[] getSupportedVersions() {
        return ProtocolVersion.TLSv13.only();
    }

    @Override
    public int[] getSupportedCipherSuites() {
        return new int[]{
                org.bouncycastle.tls.CipherSuite.TLS_AES_128_GCM_SHA256,
                org.bouncycastle.tls.CipherSuite.TLS_AES_256_GCM_SHA384,
                org.bouncycastle.tls.CipherSuite.TLS_CHACHA20_POLY1305_SHA256
        };
    }

    @Override
    protected Vector getSNIServerNames() {
        if (serverName == null || NetUtil.isValidIpV4Address(serverName) || NetUtil.isValidIpV6Address(serverName)) {
            return null;
        }
        return TlsUtils.vectorOfOne(new ServerName(NameType.host_name, serverName.getBytes(StandardCharsets.US_ASCII)));
    }

    @Override
    protected Vector getProtocolNames() {
        if (alpn == null || alpn.isEmpty()) {
            return null;
        }
        Vector<ProtocolName> names = new Vector<>(alpn.size());
        for (String protocol : alpn) {
            names.addElement(ProtocolName.asRawBytes(protocol.getBytes(StandardCharsets.US_ASCII)));
        }
        return names;
    }

    @Override
    protected Vector getSupportedGroups(Vector namedGroupRoles) {
        return TlsUtils.vectorOfOne(Integers.valueOf(NamedGroup.x25519));
    }

    @Override
    public Vector getEarlyKeyShareGroups() {
        return TlsUtils.vectorOfOne(Integers.valueOf(NamedGroup.x25519));
    }

    @Override
    public TlsAuthentication getAuthentication() {
        return this;
    }

    @Override
    public void notifyServerCertificate(TlsServerCertificate serverCertificate) throws IOException {
        org.bouncycastle.tls.Certificate certificate = serverCertificate.getCertificate();
        if (certificate == null || certificate.isEmpty()) {
            throw new TlsFatalAlert(AlertDescription.handshake_failure, "REALITY server provided no certificate");
        }
        byte[] authKey = session.authKey();
        if (authKey == null) {
            throw new TlsFatalAlert(AlertDescription.internal_error, "REALITY auth key is not initialized");
        }

        TlsCertificate tlsCertificate = certificate.getCertificateAt(0);
        byte[] certificateDer = tlsCertificate.getEncoded();
        byte[] publicKey = RealityAuth.ed25519PublicKey(certificateDer);
        if (publicKey == null) {
            throw new RealityAuthException("REALITY: unexpected server certificate key algorithm");
        }

        byte[] signature = RealityAuth.certificateSignature(certificateDer);
        if (!RealityAuth.verifyServerPublicKey(authKey, publicKey, signature)) {
            throw new RealityAuthException("REALITY: server is not authenticated, connection was not handled by our server");
        }
        session.authenticated(true);
    }

    @Override
    public TlsCredentials getClientCredentials(CertificateRequest certificateRequest) {
        return null;
    }
}
