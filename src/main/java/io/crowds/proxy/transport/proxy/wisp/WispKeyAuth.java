package io.crowds.proxy.transport.proxy.wisp;

import io.crowds.util.Hash;
import io.crowds.util.Strs;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

/**
 * ed25519 key material which is used by the public/private key authentication extension.
 */
public class WispKeyAuth {
    private final Ed25519PrivateKeyParameters privateKey;
    private final Ed25519PublicKeyParameters publicKey;
    private final byte[] publicKeyHash;

    private WispKeyAuth(Ed25519PrivateKeyParameters privateKey) {
        this.privateKey = privateKey;
        this.publicKey = privateKey.generatePublicKey();
        this.publicKeyHash = Hash.sha256(publicKey.getEncoded());
    }

    public static boolean hasKey(WispOption option) {
        return !Strs.isBlank(option.getPrivateKey()) || !Strs.isBlank(option.getPrivateKeyHex());
    }

    public static WispKeyAuth load(WispOption option) {
        if (!Strs.isBlank(option.getPrivateKeyHex())) {
            return new WispKeyAuth(fromHex(option.getPrivateKeyHex()));
        }
        if (!Strs.isBlank(option.getPrivateKey())) {
            return new WispKeyAuth(fromFile(Path.of(option.getPrivateKey().trim())));
        }
        throw new WispException("wisp key authentication requires a private key");
    }

    private static Ed25519PrivateKeyParameters fromHex(String hex) {
        String normalized = hex.trim();
        if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }
        normalized = normalized.replaceAll("[:\\s]", "");
        byte[] bytes;
        try {
            bytes = HexFormat.of().parseHex(normalized);
        } catch (IllegalArgumentException e) {
            throw new WispException("invalid hex private key", e);
        }
        return fromRaw(bytes);
    }

    private static Ed25519PrivateKeyParameters fromRaw(byte[] bytes) {
        //32 bytes: seed, 64 bytes: seed||publicKey (libsodium format)
        if (bytes.length == 32 || bytes.length == 64) {
            byte[] seed = new byte[32];
            System.arraycopy(bytes, 0, seed, 0, 32);
            return new Ed25519PrivateKeyParameters(seed, 0);
        }
        throw new WispException("invalid ed25519 private key length: " + bytes.length);
    }

    private static Ed25519PrivateKeyParameters fromFile(Path path) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new WispException("unable to read private key: " + path, e);
        }
        AsymmetricKeyParameter keyParameter = parseKey(bytes);
        if (keyParameter instanceof Ed25519PrivateKeyParameters ed25519) {
            return ed25519;
        }
        throw new WispException("the private key %s is not a ed25519 key".formatted(path));
    }

    private static AsymmetricKeyParameter parseKey(byte[] bytes) {
        String content = new String(bytes, StandardCharsets.US_ASCII);
        if (content.contains("-----BEGIN")) {
            try (PEMParser parser = new PEMParser(new StringReader(content))) {
                Object object = parser.readObject();
                if (object instanceof PEMKeyPair pemKeyPair) {
                    return PrivateKeyFactory.createKey(pemKeyPair.getPrivateKeyInfo());
                } else if (object instanceof PrivateKeyInfo privateKeyInfo) {
                    return PrivateKeyFactory.createKey(privateKeyInfo);
                }
                throw new WispException("unsupported pem object: " + (object == null ? "null" : object.getClass().getName()));
            } catch (IOException e) {
                throw new WispException("unable to parse pem private key", e);
            }
        }
        try {
            return PrivateKeyFactory.createKey(bytes);
        } catch (IOException e) {
            throw new WispException("unable to parse private key", e);
        }
    }

    public byte[] publicKeyHash() {
        return publicKeyHash.clone();
    }

    public byte[] sign(byte[] data) {
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, privateKey);
        signer.update(data, 0, data.length);
        return signer.generateSignature();
    }
}
