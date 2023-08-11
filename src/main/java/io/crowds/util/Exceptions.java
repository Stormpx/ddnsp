package io.crowds.util;

import io.netty.channel.ChannelException;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.util.Objects;
import java.util.regex.Pattern;

public class Exceptions {
    private static final Pattern IGNORABLE_SOCKET_ERROR_MESSAGE = Pattern.compile(
            "(?:connection.*(?:reset|closed|abort|broken)|broken.*pipe)", Pattern.CASE_INSENSITIVE);

    private static final Pattern IGNORABLE_TLS_ERROR_MESSAGE = Pattern.compile(
            "(?:closed already)", Pattern.CASE_INSENSITIVE);

    public static boolean isExpected(Throwable cause) {
        Objects.requireNonNull(cause, "cause");

        // We do not need to log every exception because some exceptions are expected to occur.

        if (cause instanceof ClosedChannelException) {
            // Can happen when attempting to write to a channel closed by the other end.
            return true;
        }

        if (cause instanceof UnknownHostException){
            return true;
        }

        final String msg = cause.getMessage();
        if (msg != null) {
            if ((cause instanceof IOException || cause instanceof ChannelException) &&
                    IGNORABLE_SOCKET_ERROR_MESSAGE.matcher(msg).find()) {
                // Can happen when socket error occurs.
                return true;
            }

            if (cause instanceof SSLException && IGNORABLE_TLS_ERROR_MESSAGE.matcher(msg).find()) {
                // Can happen when disconnected prematurely.
                return true;
            }
        }

        return false;
    }

    public static boolean shouldLogMessage(Throwable cause){
        if (cause instanceof UnknownHostException){
            return true;
        }
        return false;
    }
}
