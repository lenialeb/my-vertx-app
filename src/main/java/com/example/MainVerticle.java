package com.example;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * MainVerticle initializes the application and sets up the routes.
 */
public class MainVerticle extends AbstractVerticle {
  private MySQLService mySQLService;

  @Override
  public void start(Promise<Void> startPromise) {
      mySQLService = new MySQLService(vertx);
      RouteHandler routeHandler = new RouteHandler(vertx, mySQLService);

      Router router = routeHandler.setupRoutes(vertx);
      vertx.createHttpServer().requestHandler(router).listen(8888, http -> {
          if (http.succeeded()) {
              System.out.println("HTTP server started on http://localhost:8888");
              startPromise.complete();
          } else {
              startPromise.fail(http.cause());
          }
      });
  }
}