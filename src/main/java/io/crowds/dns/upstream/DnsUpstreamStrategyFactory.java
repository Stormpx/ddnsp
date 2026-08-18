package io.crowds.dns.upstream;

import io.crowds.dns.ClientOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DnsUpstreamStrategyFactory {

    private static final Logger logger = LoggerFactory.getLogger(DnsUpstreamStrategyFactory.class);



    public static DnsUpstreamStrategy create(ClientOption option, List<DnsUpstream> upstreams){

        UpstreamMode mode = option.getMode();

        return switch (mode){
            case PARALLEL -> new ParallelStrategy(upstreams);
            case USE_FIRST -> new SequentialStrategy(upstreams);
            case RANDOM -> new RandomStrategy(upstreams);
            case LATENCY -> new LatencyBasedStrategy(LatencyPolicy.DEFAULT,upstreams);
        };


    }

}
