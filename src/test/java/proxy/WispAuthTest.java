package proxy;

import io.crowds.proxy.transport.proxy.wisp.WispCodec;
import io.crowds.proxy.transport.proxy.wisp.WispConnection;
import io.crowds.proxy.transport.proxy.wisp.WispConstants;
import io.crowds.proxy.transport.proxy.wisp.WispExtension;
import io.crowds.proxy.transport.proxy.wisp.WispOption;
import io.crowds.proxy.transport.proxy.wisp.WispPacket;
import io.crowds.util.Hash;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * the authentication of the wisp v2 handshake. it is driven through an {@link EmbeddedChannel}
 * because wisp-js (which is used by {@link WispTest}) does not implement the authentication extensions.
 * <p>
 * the INFO packet of the client is parsed manually, because the extensions of the client and the server
 * have a different payload (the {@link WispCodec} only decodes the server side).
 */
public class WispAuthTest {

    private static final byte[] SEED = new byte[32];

    static {
        for (int i = 0; i < SEED.length; i++) {
            SEED[i] = (byte) (i + 7);
        }
    }

    private record ClientInfo(int major, int minor, Map<Integer, byte[]> extensions) {

        private byte[] extension(int id) {
            return extensions.get(id);
        }

        private boolean has(int id) {
            return extensions.containsKey(id);
        }
    }

    private record KeyCredentials(String username, int algorithm, byte[] publicKeyHash, byte[] signature) {
    }

    private static WispOption option() {
        WispOption option = new WispOption();
        option.setName("wisp-auth-test");
        option.setProtocol("wisp");
        option.setAddress(new InetSocketAddress("127.0.0.1", 5001));
        return option;
    }

    private static ByteBuf encode(WispPacket packet) {
        EmbeddedChannel channel = new EmbeddedChannel(new WispCodec());
        try {
            Assert.assertTrue(channel.writeOutbound(packet));
            ByteBuf encoded = channel.readOutbound();
            Assert.assertNotNull(encoded);
            return encoded;
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static WispPacket.Info serverInfo(WispExtension... extensions) {
        return new WispPacket.Info(0, 2, 1, List.of(extensions));
    }

    private static int unsigned(byte b) {
        return b & 0xff;
    }

    private static String[] parsePasswordCredentials(byte[] metadata) {
        Assert.assertNotNull("the client should have sent the credentials", metadata);
        int usernameLength = unsigned(metadata[0]);
        String username = new String(metadata, 1, usernameLength, StandardCharsets.UTF_8);
        String password = new String(metadata, 1 + usernameLength, metadata.length - 1 - usernameLength,
                StandardCharsets.UTF_8);
        return new String[]{username, password};
    }

    private static KeyCredentials parseKeyCredentials(byte[] metadata) {
        Assert.assertNotNull("the client should have signed the challenge", metadata);
        int usernameLength = unsigned(metadata[0]);
        String username = new String(metadata, 1, usernameLength, StandardCharsets.UTF_8);
        int index = 1 + usernameLength;
        int algorithm = unsigned(metadata[index]);
        byte[] publicKeyHash = Arrays.copyOfRange(metadata, index + 1, index + 33);
        byte[] signature = Arrays.copyOfRange(metadata, index + 33, metadata.length);
        return new KeyCredentials(username, algorithm, publicKeyHash, signature);
    }

    private static final class Fixture implements AutoCloseable {
        private final EmbeddedChannel channel;
        private final WispConnection connection;

        private Fixture(WispOption option) {
            this.channel = new EmbeddedChannel(WispCodec.INSTANCE);
            this.connection = new WispConnection(channel.eventLoop(), option, channel);
            channel.pipeline().addLast(connection);
        }

        /**
         * feeds a packet which was sent by the server.
         */
        private void feed(WispPacket packet) {
            channel.writeInbound(encode(packet));
        }

        /**
         * reads and parses the INFO packet which the client sent.
         */
        private ClientInfo clientInfo() {
            ByteBuf encoded = channel.readOutbound();
            Assert.assertNotNull("the client should have sent an INFO packet", encoded);
            try {
                Assert.assertEquals(WispConstants.PACKET_INFO, encoded.readUnsignedByte());
                Assert.assertEquals(0, encoded.readIntLE());
                int major = encoded.readUnsignedByte();
                int minor = encoded.readUnsignedByte();
                Map<Integer, byte[]> extensions = new LinkedHashMap<>();
                while (encoded.readableBytes() >= 5) {
                    int id = encoded.readUnsignedByte();
                    int length = encoded.readIntLE();
                    Assert.assertTrue(encoded.readableBytes() >= length);
                    byte[] metadata = new byte[length];
                    encoded.readBytes(metadata);
                    extensions.put(id, metadata);
                }
                Assert.assertFalse("unexpected trailing bytes", encoded.isReadable());
                return new ClientInfo(major, minor, extensions);
            } finally {
                encoded.release();
            }
        }

        private void finishHandshake() {
            feed(new WispPacket.Continue(0, 128));
            Assert.assertTrue(connection.readyFuture().isSuccess());
        }

        private Throwable handshakeFailure() {
            Assert.assertTrue(connection.readyFuture().isDone());
            Assert.assertFalse(connection.readyFuture().isSuccess());
            return connection.readyFuture().cause();
        }

        @Override
        public void close() {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void passwordAuthTest() {
        WispOption option = option().setUsername("user").setPassword("pass");
        try (Fixture fixture = new Fixture(option)) {
            fixture.feed(serverInfo(
                    new WispExtension.Udp(),
                    new WispExtension.Motd("welcome"),
                    new WispExtension.PasswordAuth(true)));

            ClientInfo info = fixture.clientInfo();
            Assert.assertEquals(2, info.major());
            Assert.assertEquals(1, info.minor());
            Assert.assertTrue(info.has(WispConstants.EXTENSION_UDP));
            Assert.assertTrue(info.has(WispConstants.EXTENSION_STREAM_OPEN_CONFIRMATION));
            Assert.assertTrue("the client sends the motd extension without a payload",
                    info.extension(WispConstants.EXTENSION_MOTD).length == 0);

            String[] credentials = parsePasswordCredentials(info.extension(WispConstants.EXTENSION_PASSWORD_AUTH));
            Assert.assertEquals("user", credentials[0]);
            Assert.assertEquals("pass", credentials[1]);

            fixture.finishHandshake();
        }
    }

    /**
     * the credentials must not be sent when the server does not ask for them.
     */
    @Test
    public void optionalPasswordAuthWithoutCredentialsTest() {
        try (Fixture fixture = new Fixture(option())) {
            fixture.feed(serverInfo(
                    new WispExtension.Udp(),
                    new WispExtension.PasswordAuth(false)));

            ClientInfo info = fixture.clientInfo();
            Assert.assertFalse(info.has(WispConstants.EXTENSION_PASSWORD_AUTH));

            fixture.finishHandshake();
        }
    }

    @Test
    public void requiredPasswordAuthWithoutCredentialsTest() {
        try (Fixture fixture = new Fixture(option().setUsername("user"))) {
            fixture.feed(serverInfo(new WispExtension.PasswordAuth(true)));

            String message = fixture.handshakeFailure().getMessage();
            Assert.assertTrue(message, message.contains("authentication"));
            Assert.assertNull("the client must not send an INFO packet", fixture.channel.readOutbound());
        }
    }

    /**
     * the server rejects the credentials with a CLOSE packet of the reason 0xc0.
     */
    @Test
    public void passwordAuthFailedTest() {
        WispOption option = option().setUsername("user").setPassword("pass");
        try (Fixture fixture = new Fixture(option)) {
            fixture.feed(serverInfo(new WispExtension.PasswordAuth(true)));
            fixture.clientInfo();

            fixture.feed(new WispPacket.Close(0, WispConstants.CLOSE_AUTH_PASSWORD_FAILED));

            String message = fixture.handshakeFailure().getMessage();
            Assert.assertTrue(message, message.contains("invalid username/password"));
        }
    }

    @Test
    public void keyAuthTest() {
        byte[] challenge = "a-wisp-challenge".getBytes(StandardCharsets.UTF_8);
        WispOption option = option()
                .setUsername("user")
                .setPrivateKeyHex(HexFormat.of().formatHex(SEED));
        try (Fixture fixture = new Fixture(option)) {
            fixture.feed(serverInfo(new WispExtension.KeyAuth(true,
                    WispConstants.SIGN_ALGORITHM_ED25519, challenge)));

            KeyCredentials credentials =
                    parseKeyCredentials(fixture.clientInfo().extension(WispConstants.EXTENSION_KEY_AUTH));
            Assert.assertEquals("user", credentials.username());
            Assert.assertEquals(WispConstants.SIGN_ALGORITHM_ED25519, credentials.algorithm());

            byte[] publicKey = new Ed25519PrivateKeyParameters(SEED, 0).generatePublicKey().getEncoded();
            Assert.assertArrayEquals(Hash.sha256(publicKey), credentials.publicKeyHash());

            Ed25519Signer verifier = new Ed25519Signer();
            verifier.init(false, new Ed25519PublicKeyParameters(publicKey, 0));
            verifier.update(challenge, 0, challenge.length);
            Assert.assertTrue("invalid challenge signature", verifier.verifySignature(credentials.signature()));

            fixture.finishHandshake();
        }
    }

    @Test
    public void keyAuthFailedTest() {
        byte[] challenge = "a-wisp-challenge".getBytes(StandardCharsets.UTF_8);
        WispOption option = option().setPrivateKeyHex(HexFormat.of().formatHex(SEED));
        try (Fixture fixture = new Fixture(option)) {
            fixture.feed(serverInfo(new WispExtension.KeyAuth(true,
                    WispConstants.SIGN_ALGORITHM_ED25519, challenge)));
            fixture.clientInfo();

            fixture.feed(new WispPacket.Close(0, WispConstants.CLOSE_AUTH_KEY_FAILED));

            String message = fixture.handshakeFailure().getMessage();
            Assert.assertTrue(message, message.contains("invalid signature"));
        }
    }

    /**
     * the public/private key authentication is preferred over the password authentication.
     */
    @Test
    public void keyAuthIsPreferredTest() {
        byte[] challenge = "a-wisp-challenge".getBytes(StandardCharsets.UTF_8);
        WispOption option = option()
                .setUsername("user")
                .setPassword("pass")
                .setPrivateKeyHex(HexFormat.of().formatHex(SEED));
        try (Fixture fixture = new Fixture(option)) {
            fixture.feed(serverInfo(
                    new WispExtension.PasswordAuth(false),
                    new WispExtension.KeyAuth(false, WispConstants.SIGN_ALGORITHM_ED25519, challenge)));

            ClientInfo info = fixture.clientInfo();
            Assert.assertTrue(info.has(WispConstants.EXTENSION_KEY_AUTH));
            Assert.assertFalse("the client must not send both credentials",
                    info.has(WispConstants.EXTENSION_PASSWORD_AUTH));

            fixture.finishHandshake();
        }
    }

    /**
     * a server which only offers an unsupported signature algorithm must fall back to the password authentication.
     */
    @Test
    public void unsupportedSignatureAlgorithmTest() {
        byte[] challenge = "a-wisp-challenge".getBytes(StandardCharsets.UTF_8);
        WispOption option = option()
                .setUsername("user")
                .setPassword("pass")
                .setPrivateKeyHex(HexFormat.of().formatHex(SEED));
        try (Fixture fixture = new Fixture(option)) {
            fixture.feed(serverInfo(
                    new WispExtension.PasswordAuth(false),
                    new WispExtension.KeyAuth(false, 0b0000_0010, challenge)));

            ClientInfo info = fixture.clientInfo();
            Assert.assertFalse(info.has(WispConstants.EXTENSION_KEY_AUTH));
            String[] credentials = parsePasswordCredentials(info.extension(WispConstants.EXTENSION_PASSWORD_AUTH));
            Assert.assertEquals("pass", credentials[1]);

            fixture.finishHandshake();
        }
    }

    /**
     * a wisp v1 server sends the initial CONTINUE packet without any INFO packet.
     */
    @Test
    public void v1HandshakeTest() {
        try (Fixture fixture = new Fixture(option())) {
            fixture.feed(new WispPacket.Continue(0, 64));

            Assert.assertTrue(fixture.connection.readyFuture().isSuccess());
            Assert.assertEquals(64, fixture.connection.initialBuffer());
        }
    }

    @Test
    public void handshakeTimeoutTest() {
        try (Fixture fixture = new Fixture(option())) {
            Assert.assertFalse(fixture.connection.readyFuture().isDone());
            //only the handshake timeout is scheduled on the embedded event loop
            fixture.channel.advanceTimeBy(11, TimeUnit.SECONDS);
            fixture.channel.runScheduledPendingTasks();

            Assert.assertTrue(fixture.connection.readyFuture().isDone());
            Assert.assertEquals("wisp handshake timeout", fixture.connection.readyFuture().cause().getMessage());
        }
    }
}
