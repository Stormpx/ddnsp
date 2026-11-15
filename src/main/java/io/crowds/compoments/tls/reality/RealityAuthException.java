package io.crowds.compoments.tls.reality;

import java.io.IOException;

/**
 * Raised when the peer cannot prove knowledge of the REALITY authentication key, which means the
 * connection was not handled by our server.
 */
public class RealityAuthException extends IOException {

    public RealityAuthException(String message) {
        super(message);
    }
}
