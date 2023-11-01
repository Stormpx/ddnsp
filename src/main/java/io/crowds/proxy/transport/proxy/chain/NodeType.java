package io.crowds.proxy.transport.proxy.chain;

import io.crowds.proxy.transport.ProtocolOption;

public sealed interface NodeType permits NodeType.Name, NodeType.Option {

    record Name(String name) implements NodeType{ }

    record Option(ProtocolOption option) implements NodeType{}
}
