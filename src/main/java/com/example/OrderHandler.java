package com.example;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.time.LocalDateTime;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLClient;

public class OrderHandler {
    private final SQLClient jdbcClient;

    public OrderHandler(SQLClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void setupRoutes(Router router) {
        router.get("/orders").handler(this::handleGetOrders);
        router.put("/updateStatus/:id").handler(this::handleUpdateOrderStatus);
    }

    private void handleUpdateOrderStatus(RoutingContext context) {
        String id = context.pathParam("id"); 
        JsonObject body = context.getBodyAsJson(); 
      
        
        if (body.getString("status") == null) {
            context.response()
                   .setStatusCode(400)
                   .putHeader("Content-Type", "application/json")
                   .end(new JsonObject().put("error", "Status is required.").encode());
            return;
        }
      
        // Execute the query to update the order status
        executeQuery("UPDATE orders SET status = ? WHERE id = ?", // Fixed SQL syntax
                     new JsonArray().add(body.getString("status")).add(id),
                     context, "Status updated");
      }

    private void handleGetOrders(RoutingContext context) {
  jdbcClient.query("SELECT * FROM orders", res -> {
      if (res.succeeded()) {
          JsonArray ordersArray = new JsonArray();
          res.result().getRows().forEach(row -> {
              if (row.getValue("created_at") instanceof LocalDateTime) {
                  LocalDateTime dateTime = (LocalDateTime) row.getValue("created_at");
                  row.put("created_at", dateTime.toString()); 
              }
              if (row.getValue("name") instanceof String) {
                  row.put("name", row.getValue("name")); 
              }
              ordersArray.add(row);
          });
          context.response()
              .putHeader("Content-Type", "application/json")
              .setStatusCode(200) 
              .end(ordersArray.encodePrettily()); 
      } else {
          context.response()
              .setStatusCode(500)
              .putHeader("Content-Type", "application/json")
              .end(new JsonObject().put("error", "Error retrieving orders: " + res.cause().getMessage()).encode());
      }
  });
}
private void executeQuery(String query, JsonArray params, RoutingContext context, String successMessage) {
    jdbcClient.updateWithParams(query, params, res -> {
      if (res.succeeded()) {
        context.response().end(successMessage);
      } else {
        context.response().setStatusCode(500).end(res.cause().getMessage());
      }
    });
  }
}