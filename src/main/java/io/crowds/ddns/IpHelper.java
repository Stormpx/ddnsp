package io.crowds.ddns;

import io.vertx.core.Future;

public interface IpHelper {


    Future<String> getCurIpv4();


}
