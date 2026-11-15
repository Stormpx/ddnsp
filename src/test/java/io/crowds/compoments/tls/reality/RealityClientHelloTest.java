package io.crowds.compoments.tls.reality;

import org.bouncycastle.math.ec.rfc7748.X25519;
import org.junit.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Locks the invariant that the session id written on the wire, the one hashed into the handshake
 * transcript and the one used as AEAD additional data are all derived from the same ClientHello bytes.
 */
public class RealityClientHelloTest {

    private static final int RECORD_HEADER_LENGTH = 5;
    private static final int RANDOM_OFFSET = RECORD_HEADER_LENGTH + 4 + 2;
    private static final int SESSION_ID_LENGTH_OFFSET = RANDOM_OFFSET + 32;
    private static final int SESSION_ID_OFFSET = SESSION_ID_LENGTH_OFFSET + 1;
    private static final int SESSION_ID_HANDSHAKE_OFFSET = SESSION_ID_OFFSET - RECORD_HEADER_LENGTH;

    @Test
    public void sessionIdCarriesRealityToken() throws Exception {
        byte[] serverPrivateKey = new byte[32];
        X25519.generatePrivateKey(new SecureRandom(), serverPrivateKey);
        byte[] serverPublicKey = new byte[32];
        X25519.generatePublicKey(serverPrivateKey, 0, serverPublicKey, 0);

        byte[] shortId = {0x01, 0x23, 0x45, 0x67, 0x00, 0x00, 0x00, 0x00};
        RealityOption option = new RealityOption()
                .setPublicKey(serverPublicKey)
                .setShortId(shortId)
                .setClientVersion("26.11.9");
        RealitySession session = new RealitySession(option);
        RealityClientProtocol protocol = new RealityClientProtocol(session);

        long before = System.currentTimeMillis() / 1000;
        protocol.connect(new RealityTlsClient(session, "test.ddnsp.org", List.of("h2")));
        byte[] clientHelloRecord = readAll(protocol);
        long after = System.currentTimeMillis() / 1000;

        assertEquals(0x16, clientHelloRecord[0] & 0xff);
        assertEquals(0x01, clientHelloRecord[RECORD_HEADER_LENGTH] & 0xff);
        assertEquals(32, clientHelloRecord[SESSION_ID_LENGTH_OFFSET] & 0xff);

        byte[] clientRandom = Arrays.copyOfRange(clientHelloRecord, RANDOM_OFFSET, RANDOM_OFFSET + 32);
        byte[] sessionId = Arrays.copyOfRange(clientHelloRecord, SESSION_ID_OFFSET, SESSION_ID_OFFSET + 32);

        byte[] sharedSecret = session.crypto().x25519().agreement(serverPublicKey);
        byte[] authKey = RealityAuth.authKey(sharedSecret, clientRandom);
        assertArrayEquals(authKey, session.authKey());

        int handshakeLength = ((clientHelloRecord[3] & 0xff) << 8) | (clientHelloRecord[4] & 0xff);
        byte[] aad = Arrays.copyOfRange(clientHelloRecord, RECORD_HEADER_LENGTH, RECORD_HEADER_LENGTH + handshakeLength);
        Arrays.fill(aad, SESSION_ID_HANDSHAKE_OFFSET, SESSION_ID_HANDSHAKE_OFFSET + 32, (byte) 0);

        byte[] plaintext = gcmOpen(authKey, Arrays.copyOfRange(clientRandom, 20, 32), sessionId, aad);
        assertArrayEquals(new byte[]{26, 11, 9, 0}, Arrays.copyOfRange(plaintext, 0, 4));
        assertArrayEquals(shortId, Arrays.copyOfRange(plaintext, 8, 16));

        long timestamp = ((long) (plaintext[4] & 0xff) << 24) | ((plaintext[5] & 0xff) << 16)
                | ((plaintext[6] & 0xff) << 8) | (plaintext[7] & 0xff);
        assertTrue(timestamp >= before && timestamp <= after);
        assertFalse(session.isAuthenticated());
    }

    private static byte[] readAll(RealityClientProtocol protocol) throws Exception {
        int available = protocol.getAvailableOutputBytes();
        assertTrue("client hello is expected to be produced by connect()", available > 0);
        byte[] output = new byte[available];
        int offset = 0;
        while (offset < available) {
            int read = protocol.readOutput(output, offset, available - offset);
            if (read <= 0) {
                break;
            }
            offset += read;
        }
        return Arrays.copyOf(output, offset);
    }

    private static byte[] gcmOpen(byte[] key, byte[] nonce, byte[] cipherText, byte[] aad) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(aad);
        return cipher.doFinal(cipherText);
    }
}
