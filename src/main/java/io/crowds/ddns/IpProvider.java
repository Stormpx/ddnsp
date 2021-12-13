package io.crowds.ddns;

import io.vertx.core.Future;

public interface IpProvider {


    Future<String> getCurIpv4();


}
