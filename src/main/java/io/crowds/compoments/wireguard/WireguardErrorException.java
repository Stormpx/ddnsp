package io.crowds.compoments.wireguard;

import java.io.IOException;

public class WireguardErrorException extends IOException {
    private final WireGuardError error;

    public WireguardErrorException(WireGuardError error) {
        this.error = error;
    }

    public WireguardErrorException(WireGuardError error,String message) {
        super(message);
        this.error = error;
    }

    public WireGuardError getError() {
        return error;
    }
}
