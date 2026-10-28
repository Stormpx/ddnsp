package proxy;

import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.proxy.ProtocolOptionFactory;
import io.crowds.proxy.transport.proxy.wisp.WispCodec;
import io.crowds.proxy.transport.proxy.wisp.WispConstants;
import io.crowds.proxy.transport.proxy.wisp.WispExtension;
import io.crowds.proxy.transport.proxy.wisp.WispKeyAuth;
import io.crowds.proxy.transport.proxy.wisp.WispOption;
import io.crowds.proxy.transport.proxy.wisp.WispPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

public class WispProtocolTest {

    private static WispPacket roundTrip(WispPacket packet) {
        EmbeddedChannel channel = new EmbeddedChannel(new WispCodec());
        try {
            Assert.assertTrue(channel.writeOutbound(packet));
            ByteBuf encoded = channel.readOutbound();
            Assert.assertNotNull(encoded);
            Assert.assertTrue(channel.writeInbound(encoded));
            WispPacket decoded = channel.readInbound();
            Assert.assertNotNull(decoded);
            return decoded;
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void connectPacketCodecTest() {
        WispPacket decoded = roundTrip(new WispPacket.Connect(3, WispConstants.STREAM_TYPE_UDP, "example.com", 443));
        Assert.assertTrue(decoded instanceof WispPacket.Connect);
        WispPacket.Connect connect = (WispPacket.Connect) decoded;
        Assert.assertEquals(3, connect.streamId());
        Assert.assertEquals(WispConstants.STREAM_TYPE_UDP, connect.streamType());
        Assert.assertEquals(443, connect.port());
        Assert.assertEquals("example.com", connect.hostname());
    }

    @Test
    public void dataPacketCodecTest() {
        ByteBuf content = Unpooled.buffer().writeBytes("hello".getBytes(StandardCharsets.UTF_8));
        WispPacket decoded = roundTrip(new WispPacket.Data(7, content));
        Assert.assertTrue(decoded instanceof WispPacket.Data);
        WispPacket.Data data = (WispPacket.Data) decoded;
        Assert.assertEquals(7, data.streamId());
        Assert.assertEquals("hello", data.payload().toString(StandardCharsets.UTF_8));
        data.release();
    }

    @Test
    public void continueAndClosePacketCodecTest() {
        WispPacket decoded = roundTrip(new WispPacket.Continue(9, 128));
        Assert.assertTrue(decoded instanceof WispPacket.Continue);
        Assert.assertEquals(9, decoded.streamId());
        Assert.assertEquals(128, ((WispPacket.Continue) decoded).bufferRemaining());

        decoded = roundTrip(new WispPacket.Close(0, WispConstants.CLOSE_AUTH_REQUIRED));
        Assert.assertTrue(decoded instanceof WispPacket.Close);
        Assert.assertEquals(0, decoded.streamId());
        Assert.assertEquals(WispConstants.CLOSE_AUTH_REQUIRED, ((WispPacket.Close) decoded).reason());
    }

    @Test
    public void infoPacketCodecTest() {
        byte[] challenge = "challenge".getBytes(StandardCharsets.UTF_8);
        WispPacket decoded = roundTrip(new WispPacket.Info(0, 2, 1, List.of(
                new WispExtension.Udp(),
                new WispExtension.StreamOpenConfirmation(),
                new WispExtension.Motd("welcome"),
                new WispExtension.PasswordAuth(true),
                new WispExtension.KeyAuth(true, WispConstants.SIGN_ALGORITHM_ED25519, challenge)
        )));
        Assert.assertTrue(decoded instanceof WispPacket.Info);
        WispPacket.Info info = (WispPacket.Info) decoded;
        Assert.assertEquals(2, info.major());
        Assert.assertEquals(1, info.minor());
        Assert.assertEquals(5, info.extensions().size());
        Assert.assertTrue(info.extensions().get(0) instanceof WispExtension.Udp);
        Assert.assertTrue(info.extensions().get(1) instanceof WispExtension.StreamOpenConfirmation);
        Assert.assertEquals("welcome", ((WispExtension.Motd) info.extensions().get(2)).message());
        Assert.assertTrue(((WispExtension.PasswordAuth) info.extensions().get(3)).required());
        WispExtension.KeyAuth keyAuth = (WispExtension.KeyAuth) info.extensions().get(4);
        Assert.assertTrue(keyAuth.required());
        Assert.assertEquals(WispConstants.SIGN_ALGORITHM_ED25519, keyAuth.algorithms());
        Assert.assertArrayEquals(challenge, keyAuth.challenge());
    }

    @Test
    public void clientInfoPacketCodecTest() {
        EmbeddedChannel channel = new EmbeddedChannel(new WispCodec());
        try {
            Assert.assertTrue(channel.writeOutbound(new WispPacket.Info(0, 2, 1, List.of(
                    new WispExtension.Udp(),
                    new WispExtension.Motd(null),
                    new WispExtension.StreamOpenConfirmation(),
                    new WispExtension.PasswordAuthCredentials("user", "pass")
            ))));
            ByteBuf encoded = channel.readOutbound();
            try {
                Assert.assertEquals(WispConstants.PACKET_INFO, encoded.readUnsignedByte());
                Assert.assertEquals(0, encoded.readIntLE());
                Assert.assertEquals(2, encoded.readUnsignedByte());
                Assert.assertEquals(1, encoded.readUnsignedByte());
                //udp
                Assert.assertEquals(WispConstants.EXTENSION_UDP, encoded.readUnsignedByte());
                Assert.assertEquals(0, encoded.readIntLE());
                //motd: the client sends it without a payload
                Assert.assertEquals(WispConstants.EXTENSION_MOTD, encoded.readUnsignedByte());
                Assert.assertEquals(0, encoded.readIntLE());
                //stream open confirmation
                Assert.assertEquals(WispConstants.EXTENSION_STREAM_OPEN_CONFIRMATION, encoded.readUnsignedByte());
                Assert.assertEquals(0, encoded.readIntLE());
                //password credentials: username length(uint8) + username + password
                Assert.assertEquals(WispConstants.EXTENSION_PASSWORD_AUTH, encoded.readUnsignedByte());
                Assert.assertEquals(1 + 4 + 4, encoded.readIntLE());
                Assert.assertEquals(4, encoded.readUnsignedByte());
                Assert.assertEquals("user", encoded.toString(encoded.readerIndex(), 4, StandardCharsets.UTF_8));
                encoded.skipBytes(4);
                Assert.assertEquals("pass", encoded.toString(encoded.readerIndex(), 4, StandardCharsets.UTF_8));
                encoded.skipBytes(4);
                Assert.assertFalse(encoded.isReadable());
            } finally {
                encoded.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void keyAuthCredentialsEncodeTest() {
        EmbeddedChannel channel = new EmbeddedChannel(new WispCodec());
        try {
            byte[] publicKeyHash = new byte[32];
            byte[] signature = new byte[64];
            Assert.assertTrue(channel.writeOutbound(new WispPacket.Info(0, 2, 1, List.of(
                    new WispExtension.KeyAuthCredentials("user", WispConstants.SIGN_ALGORITHM_ED25519, publicKeyHash, signature)
            ))));
            ByteBuf encoded = channel.readOutbound();
            try {
                encoded.skipBytes(5 + 2);
                Assert.assertEquals(WispConstants.EXTENSION_KEY_AUTH, encoded.readUnsignedByte());
                Assert.assertEquals(1 + 4 + 1 + 32 + 64, encoded.readIntLE());
                Assert.assertEquals(4, encoded.readUnsignedByte());
                encoded.skipBytes(4 + 1 + 32 + 64);
                Assert.assertFalse(encoded.isReadable());
            } finally {
                encoded.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void unknownExtensionShouldBeIgnoredTest() {
        EmbeddedChannel channel = new EmbeddedChannel(new WispCodec());
        try {
            ByteBuf packet = channel.alloc().buffer();
            packet.writeByte(WispConstants.PACKET_INFO);
            packet.writeIntLE(0);
            packet.writeByte(2);
            packet.writeByte(1);
            packet.writeByte(0x77);       //unknown extension
            packet.writeIntLE(3);
            packet.writeBytes(new byte[]{1, 2, 3});
            packet.writeByte(WispConstants.EXTENSION_UDP);
            packet.writeIntLE(0);
            Assert.assertTrue(channel.writeInbound(packet));
            WispPacket.Info info = channel.readInbound();
            Assert.assertEquals(1, info.extensions().size());
            Assert.assertTrue(info.extensions().get(0) instanceof WispExtension.Udp);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void ed25519KeyAuthTest() {
        byte[] seed = new byte[32];
        for (int i = 0; i < seed.length; i++) {
            seed[i] = (byte) (i + 1);
        }
        WispOption option = new WispOption().setPrivateKeyHex(HexFormat.of().formatHex(seed));
        WispKeyAuth keyAuth = WispKeyAuth.load(option);
        byte[] publicKey = new Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().getEncoded();
        Assert.assertArrayEquals(io.crowds.util.Hash.sha256(publicKey), keyAuth.publicKeyHash());

        byte[] challenge = "a-wisp-challenge".getBytes(StandardCharsets.UTF_8);
        byte[] signature = keyAuth.sign(challenge);
        Assert.assertEquals(64, signature.length);

        Ed25519Signer verifier = new Ed25519Signer();
        verifier.init(false, new Ed25519PublicKeyParameters(publicKey, 0));
        verifier.update(challenge, 0, challenge.length);
        Assert.assertTrue(verifier.verifySignature(signature));
    }

    /**
     * the libsodium style 64 bytes private key (seed||publicKey) must be accepted too.
     */
    @Test
    public void ed25519SeedAndPublicKeyHexTest() {
        byte[] seed = new byte[32];
        for (int i = 0; i < seed.length; i++) {
            seed[i] = (byte) (i + 3);
        }
        byte[] publicKey = new Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().getEncoded();
        byte[] secretKey = new byte[64];
        System.arraycopy(seed, 0, secretKey, 0, 32);
        System.arraycopy(publicKey, 0, secretKey, 32, 32);

        WispKeyAuth keyAuth = WispKeyAuth.load(new WispOption().setPrivateKeyHex(HexFormat.of().formatHex(secretKey)));
        Assert.assertArrayEquals(io.crowds.util.Hash.sha256(publicKey), keyAuth.publicKeyHash());
    }

    @Test
    public void wispOptionParseTest() {
        io.vertx.core.json.JsonObject json = new io.vertx.core.json.JsonObject()
                .put("name", "wisp-test")
                .put("protocol", "wisp")
                .put("host", "wisp.example.com")
                .put("port", 443)
                .put("username", "user")
                .put("password", "pass")
                .put("privateKey", "/tmp/key.pem")
                .put("privateKeyHex", "0102")
                .put("network", "ws")
                .put("transport", new io.vertx.core.json.JsonObject()
                        .put("ws", new io.vertx.core.json.JsonObject()
                                .put("path", "/wisp/")
                                .put("secProtocol", "wisp-custom")));
        ProtocolOption protocolOption = ProtocolOptionFactory.newOption(json);
        Assert.assertTrue(protocolOption instanceof WispOption);
        WispOption wispOption = (WispOption) protocolOption;
        Assert.assertEquals("wisp-test", wispOption.getName());
        Assert.assertEquals("wisp.example.com", wispOption.getAddress().getHostString());
        Assert.assertEquals(443, wispOption.getAddress().getPort());
        Assert.assertEquals("user", wispOption.getUsername());
        Assert.assertEquals("pass", wispOption.getPassword());
        Assert.assertEquals("/tmp/key.pem", wispOption.getPrivateKey());
        Assert.assertEquals("0102", wispOption.getPrivateKeyHex());
        Assert.assertEquals("ws", wispOption.getNetwork());
        Assert.assertEquals("/wisp/", wispOption.getTransport().getWs().getPath());
        Assert.assertEquals("wisp-custom", wispOption.getTransport().getWs().getSecProtocol());
    }

    @Test(expected = RuntimeException.class)
    public void wispOptionParseWithoutHostTest() {
        ProtocolOptionFactory.newOption(new io.vertx.core.json.JsonObject()
                .put("name", "wisp-test")
                .put("protocol", "wisp")
                .put("port", 443));
    }
}
