package io.crowds.api;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.Future;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authentication.Credentials;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.APIKeyHandler;
import io.vertx.ext.web.handler.AuthenticationHandler;
import io.vertx.ext.web.handler.HttpException;

import java.util.Objects;

public class TokenAuthHandler implements AuthenticationHandler {

    private final ApiOption apiOption;
    private final APIKeyHandler apiKeyHandler;

    public TokenAuthHandler(ApiOption apiOption) {
        this.apiOption = apiOption;
        this.apiKeyHandler = APIKeyHandler.create(this::authenticate).header(HttpHeaders.AUTHORIZATION.toString())
                .tokenExtractor(value -> {
                    if (value.startsWith("Bearer "))
                        return Future.succeededFuture(value.substring(value.indexOf("Bearer ")));
                    return Future.failedFuture(new HttpException(401,"token not found"));
                });
    }

    Future<User> authenticate(Credentials credentials){
        if (credentials instanceof TokenCredentials tokenCredentials){
            String token = tokenCredentials.getToken();
            if (Objects.equals(apiOption.getToken(),token)){
                return Future.succeededFuture(User.fromToken(token));
            }
        }
        return Future.failedFuture(new HttpException(401,"Invalid Token"));
    }

    @Override
    public void handle(RoutingContext event) {
        apiKeyHandler.handle(event);
    }
}
