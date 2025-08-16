package io.crowds.dns.server;

import java.net.InetSocketAddress;

public sealed interface LocalMsgType permits LocalMsgType.Datagram, LocalMsgType.Stream {

    record Stream(InetSocketAddress sender) implements LocalMsgType{}

    record Datagram() implements LocalMsgType {}
}
