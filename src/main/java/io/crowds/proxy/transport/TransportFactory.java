package io.crowds.proxy.transport;

import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.transport.ws.WebsocketTransport;
import io.crowds.proxy.transport.ws.WsOption;
import io.crowds.util.Strs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransportFactory {
    private final static Logger logger= LoggerFactory.getLogger(TransportFactory.class);

    public static Transport newTransport(ProtocolOption protocolOption, ChannelCreator channelCreator){
        if (Strs.isBlank(protocolOption.getNetwork())){
            return new DirectTransport(protocolOption,channelCreator);
        }
        TransportOption transport = protocolOption.getTransport();
        if ("ws".equalsIgnoreCase(protocolOption.getNetwork())){
            WsOption ws = transport.getWs();
            if (ws==null){
                logger.warn("ws config not found. use default instead.");
                transport.setWs(new WsOption());
            }
            return new WebsocketTransport(protocolOption,channelCreator);
        }else{
            throw new IllegalArgumentException("no such transport: "+protocolOption.getNetwork());
        }
    }

}
