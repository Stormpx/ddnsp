package io.crowds.proxy.transport.proxy.wisp;

public final class WispConstants {

    private WispConstants() {
    }

    /**
     * packet types
     */
    public static final int PACKET_CONNECT = 0x01;
    public static final int PACKET_DATA = 0x02;
    public static final int PACKET_CONTINUE = 0x03;
    public static final int PACKET_CLOSE = 0x04;
    public static final int PACKET_INFO = 0x05;

    /**
     * stream types which are used by the CONNECT packet
     */
    public static final int STREAM_TYPE_TCP = 0x01;
    public static final int STREAM_TYPE_UDP = 0x02;

    /**
     * close reasons
     */
    public static final int CLOSE_UNSPECIFIED = 0x01;
    public static final int CLOSE_VOLUNTARY = 0x02;
    public static final int CLOSE_NETWORK_ERROR = 0x03;
    public static final int CLOSE_INCOMPATIBLE_EXTENSIONS = 0x04;

    public static final int CLOSE_INVALID_INFO = 0x41;
    public static final int CLOSE_UNREACHABLE_HOST = 0x42;
    public static final int CLOSE_CONNECT_TIMEOUT = 0x43;
    public static final int CLOSE_CONNECTION_REFUSED = 0x44;
    public static final int CLOSE_TRANSFER_TIMEOUT = 0x47;
    public static final int CLOSE_BLOCKED = 0x48;
    public static final int CLOSE_THROTTLED = 0x49;

    public static final int CLOSE_CLIENT_ERROR = 0x81;

    public static final int CLOSE_AUTH_PASSWORD_FAILED = 0xc0;
    public static final int CLOSE_AUTH_KEY_FAILED = 0xc1;
    public static final int CLOSE_AUTH_REQUIRED = 0xc2;

    /**
     * protocol extension ids
     */
    public static final int EXTENSION_UDP = 0x01;
    public static final int EXTENSION_PASSWORD_AUTH = 0x02;
    public static final int EXTENSION_KEY_AUTH = 0x03;
    public static final int EXTENSION_MOTD = 0x04;
    public static final int EXTENSION_STREAM_OPEN_CONFIRMATION = 0x05;

    /**
     * signature algorithms of the key authentication extension
     */
    public static final int SIGN_ALGORITHM_ED25519 = 0x01;

    public static final int WISP_VERSION_MAJOR = 2;
    public static final int WISP_VERSION_MINOR = 1;

    public static String closeReason(int reason) {
        return switch (reason) {
            case CLOSE_UNSPECIFIED -> "unspecified";
            case CLOSE_VOLUNTARY -> "voluntary stream closure";
            case CLOSE_NETWORK_ERROR -> "unexpected stream closure due to a network error";
            case CLOSE_INCOMPATIBLE_EXTENSIONS -> "incompatible extensions";
            case CLOSE_INVALID_INFO -> "stream creation failed due to invalid information";
            case CLOSE_UNREACHABLE_HOST -> "stream creation failed due to an unreachable destination host";
            case CLOSE_CONNECT_TIMEOUT -> "stream creation timed out";
            case CLOSE_CONNECTION_REFUSED -> "stream creation failed due to the destination server refusing the connection";
            case CLOSE_TRANSFER_TIMEOUT -> "tcp data transfer timed out";
            case CLOSE_BLOCKED -> "stream destination address/domain is intentionally blocked by the proxy server";
            case CLOSE_THROTTLED -> "connection throttled by the server";
            case CLOSE_CLIENT_ERROR -> "the client has encountered an unexpected error";
            case CLOSE_AUTH_PASSWORD_FAILED -> "authentication failed due to invalid username/password";
            case CLOSE_AUTH_KEY_FAILED -> "authentication failed due to invalid signature";
            case CLOSE_AUTH_REQUIRED -> "authentication required but the client did not provide credentials";
            default -> "unknown reason(" + reason + ")";
        };
    }
}
