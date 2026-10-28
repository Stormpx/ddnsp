package io.crowds.proxy.transport.proxy.wisp;

/**
 * a protocol extension of the wisp INFO packet.
 * <p>
 * extensions which are sent by the server and extensions which are sent by the client have a different payload,
 * so both directions are represented by a separate record.
 */
public sealed interface WispExtension {

    int id();

    /**
     * the client and the server both support udp streams.
     */
    record Udp() implements WispExtension {
        @Override
        public int id() {
            return WispConstants.EXTENSION_UDP;
        }
    }

    /**
     * the server confirms that a stream has been opened successfully.
     */
    record StreamOpenConfirmation() implements WispExtension {
        @Override
        public int id() {
            return WispConstants.EXTENSION_STREAM_OPEN_CONFIRMATION;
        }
    }

    /**
     * server -> client: the welcome message of the server.
     * the client only sends this extension without a message to indicate that it is able to display it.
     */
    record Motd(String message) implements WispExtension {
        @Override
        public int id() {
            return WispConstants.EXTENSION_MOTD;
        }
    }

    /**
     * server -> client: the server allows username/password authentication.
     */
    record PasswordAuth(boolean required) implements WispExtension {
        @Override
        public int id() {
            return WispConstants.EXTENSION_PASSWORD_AUTH;
        }
    }

    /**
     * client -> server: the username and password.
     */
    record PasswordAuthCredentials(String username, String password) implements WispExtension {
        @Override
        public int id() {
            return WispConstants.EXTENSION_PASSWORD_AUTH;
        }
    }

    /**
     * server -> client: the server allows public/private key authentication,
     * {@code algorithms} is a bit mask of the supported signature algorithms.
     */
    record KeyAuth(boolean required, int algorithms, byte[] challenge) implements WispExtension {
        @Override
        public int id() {
            return WispConstants.EXTENSION_KEY_AUTH;
        }
    }

    /**
     * client -> server: the signature of the challenge.
     */
    record KeyAuthCredentials(String username, int algorithm, byte[] publicKeyHash, byte[] signature) implements WispExtension {
        @Override
        public int id() {
            return WispConstants.EXTENSION_KEY_AUTH;
        }
    }
}
