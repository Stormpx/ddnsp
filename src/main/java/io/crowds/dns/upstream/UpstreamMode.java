package io.crowds.dns.upstream;

/**
 * Upstream DNS server query strategy mode.
 *
 */
public enum UpstreamMode {

    /**
     * Send the query to all upstreams concurrently and use the first
     * successful response.
     */
    PARALLEL,

    /**
     * Use upstreams in order, falling back to the next one only when the
     * current one fails (or times out).
     */
    USE_FIRST,

    /**
     * Pick one upstream at random.
     */
    RANDOM,

    /**
     *
     */
    LATENCY,
    ;

    public static UpstreamMode of(String mode){
        if (mode==null){
            return null;
        }
        mode = mode.trim();
        for (UpstreamMode value : values()) {
            if (value.name().equalsIgnoreCase(mode)){
                return value;
            }
        }
        return null;
    }

}
