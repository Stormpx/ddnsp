package io.crowds.proxy.transport.proxy.tuic;

public enum UdpMode {

    NATIVE,
    QUIC
    ;
    public static UdpMode of(String mode) {
        if ("native".equalsIgnoreCase(mode)) {
            return NATIVE;
        }else if ("quic".equalsIgnoreCase(mode)) {
            return QUIC;
        }
        return null;
    }
}
