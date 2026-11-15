package io.crowds.compoments.tls.reality;

import org.bouncycastle.asn1.ASN1BitString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.edec.EdECObjectIdentifiers;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.digests.SHA512Digest;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * REALITY authentication primitives shared by the client handshake and the certificate check.
 */
final class RealityAuth {

    static final int SESSION_ID_LENGTH = 32;
    private static final byte[] HKDF_INFO = "REALITY".getBytes(StandardCharsets.US_ASCII);
    private static final int AUTH_KEY_LENGTH = 32;
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_TAG_LENGTH = GCM_TAG_BITS / 8;
    private static final int NONCE_OFFSET = 20;
    private static final int SALT_LENGTH = 20;

    private RealityAuth() {
    }

    static byte[] authKey(byte[] sharedSecret, byte[] clientRandom) {
        HKDFBytesGenerator generator = new HKDFBytesGenerator(new SHA256Digest());
        generator.init(new HKDFParameters(sharedSecret,
                Arrays.copyOfRange(clientRandom, 0, SALT_LENGTH), HKDF_INFO));
        byte[] authKey = new byte[AUTH_KEY_LENGTH];
        generator.generateBytes(authKey, 0, authKey.length);
        return authKey;
    }

    /**
     * 16 bytes carrying the client version, the current time and the short id, all covered by the AEAD tag.
     */
    static byte[] sessionIdPlaintext(String clientVersion, byte[] shortId, long nowSeconds) {
        byte[] version = clientVersion(clientVersion);
        byte[] plaintext = new byte[16];
        plaintext[0] = version[0];
        plaintext[1] = version[1];
        plaintext[2] = version[2];
        plaintext[3] = 0;
        plaintext[4] = (byte) (nowSeconds >>> 24);
        plaintext[5] = (byte) (nowSeconds >>> 16);
        plaintext[6] = (byte) (nowSeconds >>> 8);
        plaintext[7] = (byte) nowSeconds;
        System.arraycopy(shortId, 0, plaintext, 8, Math.min(shortId.length, 8));
        return plaintext;
    }

    static byte[] sealSessionId(byte[] authKey, byte[] clientRandom, byte[] plaintext, byte[] aad) {
        GCMBlockCipher cipher = new GCMBlockCipher(AESEngine.newInstance());
        cipher.init(true, new AEADParameters(new KeyParameter(authKey), GCM_TAG_BITS,
                Arrays.copyOfRange(clientRandom, NONCE_OFFSET, NONCE_OFFSET + 12), aad));
        byte[] sealed = new byte[plaintext.length + GCM_TAG_LENGTH];
        int len = cipher.processBytes(plaintext, 0, plaintext.length, sealed, 0);
        try {
            len += cipher.doFinal(sealed, len);
        } catch (Exception e) {
            throw new IllegalStateException("unable to seal REALITY session id", e);
        }
        return sealed;
    }

    static boolean verifyServerPublicKey(byte[] authKey, byte[] publicKey, byte[] signature) {
        HMac mac = new HMac(new SHA512Digest());
        mac.init(new KeyParameter(authKey));
        mac.update(publicKey, 0, publicKey.length);
        byte[] expected = new byte[mac.getMacSize()];
        mac.doFinal(expected, 0);
        return MessageDigest.isEqual(expected, signature);
    }

    /**
     * Reads the Ed25519 subject public key straight out of the certificate encoding. The certificate
     * produced by the server is not a conforming one (empty issuer, bogus signature), so it has to be
     * walked as plain ASN.1 instead of going through a validating parser.
     */
    static byte[] ed25519PublicKey(byte[] certificateDer) throws IOException {
        SubjectPublicKeyInfo subjectPublicKeyInfo = subjectPublicKeyInfo(certificateDer);
        return subjectPublicKeyInfo == null ? null : subjectPublicKeyInfo.getPublicKeyData().getBytes();
    }

    static SubjectPublicKeyInfo subjectPublicKeyInfo(byte[] certificateDer) throws IOException {
        ASN1Sequence tbsCertificate = tbsCertificate(certificateDer);
        for (int i = 0; i < tbsCertificate.size(); i++) {
            if (!(tbsCertificate.getObjectAt(i) instanceof ASN1Sequence field) || field.size() != 2) {
                continue;
            }
            if (!(field.getObjectAt(1) instanceof ASN1BitString)) {
                continue;
            }
            SubjectPublicKeyInfo subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(field);
            if (EdECObjectIdentifiers.id_Ed25519.equals(subjectPublicKeyInfo.getAlgorithm().getAlgorithm())) {
                return subjectPublicKeyInfo;
            }
        }
        return null;
    }

    static byte[] certificateSignature(byte[] certificateDer) throws IOException {
        ASN1Sequence certificate = ASN1Sequence.getInstance(ASN1Primitive.fromByteArray(certificateDer));
        return ASN1BitString.getInstance(certificate.getObjectAt(2)).getBytes();
    }

    private static ASN1Sequence tbsCertificate(byte[] certificateDer) throws IOException {
        ASN1Sequence certificate = ASN1Sequence.getInstance(ASN1Primitive.fromByteArray(certificateDer));
        return ASN1Sequence.getInstance(certificate.getObjectAt(0));
    }

    static byte[] clientVersion(String version) {
        String[] parts = version == null ? new String[0] : version.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("unexpected reality clientVersion: " + version);
        }
        byte[] result = new byte[3];
        for (int i = 0; i < 3; i++) {
            int value = Integer.parseInt(parts[i].trim());
            if (value < 0 || value > 255) {
                throw new IllegalArgumentException("unexpected reality clientVersion: " + version);
            }
            result[i] = (byte) value;
        }
        return result;
    }
}
