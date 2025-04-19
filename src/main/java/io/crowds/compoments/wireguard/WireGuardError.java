package io.crowds.compoments.wireguard;

public enum WireGuardError {

    DestinationBufferTooSmall,
    IncorrectPacketLength,
    UnexpectedPacket,
    WrongPacketType,
    WrongIndex,
    WrongKey,
    InvalidTai64nTimestamp,
    WrongTai64nTimestamp,
    InvalidMac,
    InvalidAeadTag,
    InvalidCounter,
    InvalidPacket,
    NoCurrentSession,
    LockFailed,
    ConnectionExpired,
    UnderLoad,
    ;

    public static WireGuardError valueOf(int e){
        for (WireGuardError error : WireGuardError.values()) {
            if (error.ordinal()==e)
                return error;
        }
        return null;
    }
}
