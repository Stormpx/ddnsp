package io.crowds.compoments.tls.reality;

import org.bouncycastle.tls.AlertDescription;
import org.bouncycastle.tls.ClientHello;
import org.bouncycastle.tls.HandshakeType;
import org.bouncycastle.tls.TlsClientProtocol;
import org.bouncycastle.tls.TlsFatalAlert;
import org.bouncycastle.tls.TlsUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Replaces the ClientHello session id with the REALITY token before the handshake transcript and the
 * wire bytes are produced, so that both always carry the same value.
 */
final class RealityClientProtocol extends TlsClientProtocol {

    private final RealitySession session;
    private boolean injected;

    RealityClientProtocol(RealitySession session) {
        this.session = session;
    }

    @Override
    protected void sendClientHelloMessage() throws IOException {
        if (!injected) {
            injected = true;
            injectSessionId();
        }
        super.sendClientHelloMessage();
    }

    private void injectSessionId() throws IOException {
        String clientVersion = session.clientVersion();
        byte[] publicKey = session.publicKey();
        byte[] shortId = session.shortId();
        ClientHello hello = this.clientHello;
        if (hello.getSessionID().length != RealityAuth.SESSION_ID_LENGTH || hello.getBindersSize() != 0) {
            throw new TlsFatalAlert(AlertDescription.internal_error, "unexpected client hello layout");
        }

        RealityX25519 agreement = session.crypto().x25519();
        if (agreement == null) {
            throw new TlsFatalAlert(AlertDescription.internal_error, "x25519 key share was not offered");
        }

        byte[] aad = encodeBlankSessionId(hello);
        byte[] authKey = RealityAuth.authKey(agreement.agreement(publicKey), hello.getRandom());
        session.authKey(authKey);
        byte[] sealedSessionId = RealityAuth.sealSessionId(authKey, hello.getRandom(),
                RealityAuth.sessionIdPlaintext(clientVersion, shortId,System.currentTimeMillis() / 1000), aad);

        this.clientHello = new ClientHello(hello.getVersion(), hello.getRandom(), sealedSessionId,
                hello.getCookie(), hello.getCipherSuites(), hello.getExtensions(), hello.getBindersSize());
    }

    /**
     * Handshake message with the session id replaced by zeros, which is what the server feeds into the
     * AEAD as additional data.
     */
    private byte[] encodeBlankSessionId(ClientHello hello) throws IOException {
        ClientHello blank = new ClientHello(hello.getVersion(), hello.getRandom(),
                new byte[RealityAuth.SESSION_ID_LENGTH], hello.getCookie(), hello.getCipherSuites(),
                hello.getExtensions(), hello.getBindersSize());
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        blank.encode(getContext(), body);
        byte[] bodyBytes = body.toByteArray();
        byte[] aad = new byte[4 + bodyBytes.length];
        aad[0] = (byte) HandshakeType.client_hello;
        TlsUtils.writeUint24(bodyBytes.length, aad, 1);
        System.arraycopy(bodyBytes, 0, aad, 4, bodyBytes.length);
        return aad;
    }
}
