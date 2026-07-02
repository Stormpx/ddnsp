package io.crowds.api;

import io.crowds.Context;
import io.crowds.proxy.ProxyServer;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class ApiServer {

    private final ApiOption apiOption;
    private final Context context;
    private final ProxyServer proxyServer;

    public ApiServer(ApiOption apiOption, Context context, ProxyServer proxyServer) {
        this.apiOption = apiOption;
        this.context = context;
        this.proxyServer = proxyServer;
    }

    public Future<Void> start() {
        Router mainRouter = Router.router(context.getVertx());
        errorHandler(mainRouter, 400);
        errorHandler(mainRouter, 401);
        errorHandler(mainRouter, 404);
        errorHandler(mainRouter, 500);
        mainRouter.route("/api/*").handler(BodyHandler.create());
        mainRouter.route("/api/*").handler(new TokenAuthHandler(apiOption));

        RouteRuleService routeRuleService = new RouteRuleService(context.getVertx(), proxyServer.getAxis().getRouter());
        mainRouter.route("/api/*").subRouter(routeRuleService);

        HttpServer server = context.getVertx().createHttpServer();
        server.requestHandler(mainRouter);
        return server.listen(apiOption.getPort(), apiOption.getHost())
                .mapEmpty();
    }

    private void errorHandler(Router router, int statusCode) {
        router.errorHandler(statusCode, ctx -> {
            String message = ctx.failure() != null ? ctx.failure().getMessage() : "error";
            ctx.json(Responses.fail(statusCode, message).toJson());
        });
    }
}
