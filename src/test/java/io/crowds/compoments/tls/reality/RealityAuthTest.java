package io.crowds.compoments.tls.reality;

import io.crowds.util.Rands;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.DERUTCTime;
import org.bouncycastle.asn1.edec.EdECObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.junit.Test;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;

import static org.junit.Assert.*;

public class RealityAuthTest {

    private static final byte[] INFO = "REALITY".getBytes(StandardCharsets.US_ASCII);

    @Test
    public void authKeyMatchesJdkHkdf() throws Exception {
        byte[] shared = Rands.genBytes(32);
        byte[] clientRandom = Rands.genBytes(32);

        byte[] expected = hkdfSha256(shared, Arrays.copyOfRange(clientRandom, 0, 20), INFO, 32);
        byte[] actual = RealityAuth.authKey(shared, clientRandom);

        assertArrayEquals(expected, actual);
    }

    @Test
    public void sealedSessionIdOpensWithJdk() throws Exception {
        byte[] authKey = Rands.genBytes(32);
        byte[] clientRandom = Rands.genBytes(32);
        RealityOption realityOption = new RealityOption().setShortId(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        byte[] plaintext = RealityAuth.sessionIdPlaintext(realityOption.getClientVersion(),realityOption.getShortId(), 1700000000L);
        byte[] aad = Rands.genBytes(240);

        byte[] sealed = RealityAuth.sealSessionId(authKey, clientRandom, plaintext, aad);

        assertEquals(32, sealed.length);
        assertArrayEquals(plaintext, gcmOpen(authKey, Arrays.copyOfRange(clientRandom, 20, 32), sealed, aad));
    }

    @Test
    public void openingWithDifferentAdditionalDataFails() throws Exception {
        byte[] authKey = Rands.genBytes(32);
        byte[] clientRandom = Rands.genBytes(32);
        RealityOption realityOption = new RealityOption();
        byte[] plaintext = RealityAuth.sessionIdPlaintext(realityOption.getClientVersion(),realityOption.getShortId(), 1700000000L);

        byte[] sealed = RealityAuth.sealSessionId(authKey, clientRandom, plaintext, Rands.genBytes(240));

        try {
            gcmOpen(authKey, Arrays.copyOfRange(clientRandom, 20, 32), sealed, Rands.genBytes(240));
            fail("expected authentication failure");
        } catch (Exception expected) {
            // the tag covers the additional data
        }
    }

    @Test
    public void serverCertificateSignatureIsHmacOverPublicKey() throws Exception {
        byte[] authKey = Rands.genBytes(32);
        byte[] publicKey = Rands.genBytes(32);
        byte[] signature = hmacSha512(authKey, publicKey);

        assertTrue(RealityAuth.verifyServerPublicKey(authKey, publicKey, signature));
        assertFalse(RealityAuth.verifyServerPublicKey(Rands.genBytes(32), publicKey, signature));

        signature[0] ^= 0x01;
        assertFalse(RealityAuth.verifyServerPublicKey(authKey, publicKey, signature));
    }

    @Test
    public void sessionIdPlaintextLayout() {
        RealityOption realityOption = new RealityOption().setClientVersion("26.11.9")
                                                         .setShortId(new byte[]{0x0a, 0x0b, 0x0c});
        byte[] plaintext = RealityAuth.sessionIdPlaintext(realityOption.getClientVersion(),realityOption.getShortId(), 0x01020304L);

        assertEquals(16, plaintext.length);
        assertArrayEquals(new byte[]{26, 11, 9, 0}, Arrays.copyOfRange(plaintext, 0, 4));
        assertArrayEquals(new byte[]{1, 2, 3, 4}, Arrays.copyOfRange(plaintext, 4, 8));
        assertArrayEquals(new byte[]{0x0a, 0x0b, 0x0c, 0, 0, 0, 0, 0}, Arrays.copyOfRange(plaintext, 8, 16));
    }

    @Test
    public void clientVersionParsing() {
        assertArrayEquals(new byte[]{26, 11, 9}, RealityAuth.clientVersion("26.11.9"));
        assertThrows(IllegalArgumentException.class, () -> RealityAuth.clientVersion("26.11"));
        assertThrows(IllegalArgumentException.class, () -> RealityAuth.clientVersion("26.11.256"));
        assertThrows(IllegalArgumentException.class, () -> RealityAuth.clientVersion(null));
    }

    @Test
    public void realityCertificateIsParsedWithoutFullValidation() throws Exception {
        byte[] publicKey = Rands.genBytes(32);
        byte[] signature = Rands.genBytes(64);
        byte[] certificate = buildRealityCertificate(publicKey, signature);

        assertArrayEquals(publicKey, RealityAuth.ed25519PublicKey(certificate));
        assertArrayEquals(signature, RealityAuth.certificateSignature(certificate));

        // the strict X.509 parser rejects the certificate the server sends, which is why RealityCrypto
        // never hands the encoding to BcTlsCertificate
        assertThrows(IllegalArgumentException.class, () -> org.bouncycastle.asn1.x509.Certificate
                .getInstance(ASN1Primitive.fromByteArray(certificate)));
    }

    /**
     * Mirrors the certificate the REALITY server derives from an empty x509.Certificate template.
     */
    private static byte[] buildRealityCertificate(byte[] publicKey, byte[] signature) throws Exception {
        ASN1EncodableVector tbsCertificate = new ASN1EncodableVector();
        tbsCertificate.add(new DERTaggedObject(true, 0, new ASN1Integer(2)));
        tbsCertificate.add(new ASN1Integer(0));
        tbsCertificate.add(new AlgorithmIdentifier(EdECObjectIdentifiers.id_Ed25519));
        tbsCertificate.add(new DERSequence(new ASN1EncodableVector()));
        tbsCertificate.add(new DERSequence(new DERUTCTime(new Date()), new DERUTCTime(new Date())));
        tbsCertificate.add(new DERSequence(new ASN1EncodableVector()));
        tbsCertificate.add(new SubjectPublicKeyInfo(new AlgorithmIdentifier(EdECObjectIdentifiers.id_Ed25519),
                new DERBitString(publicKey).getOctets()));

        ASN1EncodableVector certificate = new ASN1EncodableVector();
        certificate.add(new DERSequence(tbsCertificate));
        certificate.add(new AlgorithmIdentifier(EdECObjectIdentifiers.id_Ed25519));
        certificate.add(new DERBitString(signature));
        return new DERSequence(certificate).getEncoded(ASN1Encoding.DER);
    }

    private static byte[] gcmOpen(byte[] key, byte[] nonce, byte[] cipherText, byte[] aad) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(aad);
        return cipher.doFinal(cipherText);
    }

    private static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int length) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] pseudoRandomKey = mac.doFinal(ikm);

        mac.init(new SecretKeySpec(pseudoRandomKey, "HmacSHA256"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] block = new byte[0];
        for (int counter = 1; output.size() < length; counter++) {
            mac.update(block);
            mac.update(info);
            mac.update((byte) counter);
            block = mac.doFinal();
            output.write(block);
        }
        return Arrays.copyOf(output.toByteArray(), length);
    }

    private static byte[] hmacSha512(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(key, "HmacSHA512"));
        return mac.doFinal(data);
    }
}
