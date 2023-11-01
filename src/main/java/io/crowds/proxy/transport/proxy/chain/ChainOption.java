package io.crowds.proxy.transport.proxy.chain;

import io.crowds.proxy.transport.ProtocolOption;

import java.util.List;

public class ChainOption extends ProtocolOption {

    private List<NodeType> nodes;

    public List<NodeType> getNodes() {
        return nodes;
    }

    public ChainOption setNodes(List<NodeType> nodes) {
        this.nodes = nodes;
        return this;
    }
}
