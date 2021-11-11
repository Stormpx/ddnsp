package io.crowds.proxy.dns;

public class FakeOption {
    private String ipv4Pool;
    private String ipv6Pool;
    private String destStrategy="domain";

    public String getIpv4Pool() {
        return ipv4Pool;
    }

    public FakeOption setIpv4Pool(String ipv4Pool) {
        this.ipv4Pool = ipv4Pool;
        return this;
    }

    public String getIpv6Pool() {
        return ipv6Pool;
    }

    public FakeOption setIpv6Pool(String ipv6Pool) {
        this.ipv6Pool = ipv6Pool;
        return this;
    }

    public String getDestStrategy() {
        return destStrategy;
    }

    public FakeOption setDestStrategy(String destStrategy) {
        this.destStrategy = destStrategy;
        return this;
    }
}
