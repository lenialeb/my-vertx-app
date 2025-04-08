package com.example;

import java.sql.Date;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;

import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.core.http.HttpMethod;
import io.github.cdimascio.dotenv.Dotenv;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.handler.BodyHandler;

import io.vertx.core.Future;
public class RouteHandler {
  private final SQLClient jdbcClient;
  private final Vertx vertx;

  public RouteHandler(Vertx vertx, MySQLService mySQLService) {
      this.vertx = vertx;
      this.jdbcClient = mySQLService.getJDBCClient();
  }

  public Router setupRoutes(Vertx vertx) {
    Router router = Router.router(vertx);
  
    router.route().handler(CorsHandler.create() 
        .addOrigin("*")
        .allowedMethods(new HashSet<>(Arrays.asList(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.OPTIONS)))
        .allowedHeaders(new HashSet<>(Arrays.asList("Content-Type", "Authorization")))
        .exposedHeaders(new HashSet<>(Arrays.asList("Authorization")))); 
    router.route().handler(BodyHandler.create());
 
  
  
    UserHandler userHandler = new UserHandler(jdbcClient);
    userHandler.setupRoutes(router);

    ProductHandler productHandler = new ProductHandler(jdbcClient);
    productHandler.setupRoutes(router);
    PaymentHandler paymentHandler = new PaymentHandler(jdbcClient, vertx);
    paymentHandler.setupRoutes(router);

    OrderHandler orderHandler = new OrderHandler(jdbcClient);
    orderHandler.setupRoutes(router);
  


    
    return router;
  }
  
  



  // private void executeQuery(String query, JsonArray params, RoutingContext context, String successMessage) {
  //   jdbcClient.updateWithParams(query, params, res -> {
  //     if (res.succeeded()) {
  //       context.response().end(successMessage);
  //     } else {
  //       context.response().setStatusCode(500).end(res.cause().getMessage());
  //     }
  //   });
  // }
 
  


  



 


}
  