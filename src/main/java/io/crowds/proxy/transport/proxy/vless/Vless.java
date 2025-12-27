package io.crowds.proxy.transport.proxy.vless;

import java.util.Map;

public class Vless {

    public static final byte[] TLS_13_SUPPORTED_VERSIONS  = new byte[]{0x00, 0x2b, 0x00, 0x02, 0x03, 0x04};
    public static final byte[] TLS_CLIENT_HAND_SHAKE_START = new byte[]{0x16, 0x03};
    public static final byte[] TLS_SERVER_HAND_SHAKE_START = new byte[]{0x16, 0x03, 0x03};
    public static final byte[] TLS_APPLICATION_DATA_START = new byte[]{0x17, 0x03, 0x03};

    public static final Map<Integer,String> TLS_13_CIPHER_SUITE_DIC = Map.of(
            0x1301, "TLS_AES_128_GCM_SHA256",
            0x1302, "TLS_AES_256_GCM_SHA384",
            0x1303, "TLS_CHACHA20_POLY1305_SHA256",
            0x1304, "TLS_AES_128_CCM_SHA256",
            0x1305, "TLS_AES_128_CCM_8_SHA256"
    );


    public enum Flow{
        NONE("none"),
        XRV("xtls-rprx-vision"),
        ;

        private String value;
        Flow(String value) {
            this.value = value;
        }

        public String value(){
            return value;
        }

        public static Flow of(String value){
            for (Flow flow : values()) {
                if (flow.value.equalsIgnoreCase(value)){
                    return flow;
                }
            }
            return NONE;
        }

    }

    public static final byte TLS_HANDSHAKE_TYPE_CLIENT_HELLO = 0x01;
    public static final byte TLS_HANDSHAKE_TYPE_SERVER_HELLO = 0x02;

    public static final byte COMMAND_PADDING_CONTINUE  = 0x00;
    public static final byte COMMAND_PADDING_END       = 0x01;
    public static final byte COMMAND_PADDING_DIRECT    = 0x02;



}
