package com.example;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLClient;
import io.vertx.core.json.JsonArray;

public class CommentHandler {
    private final SQLClient jdbcClient;

    public CommentHandler(SQLClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void setupRoutes(Router router) {
        router.post("/comment/:id").handler(this:: postComment);
        router.get("/comment/:id").handler(this:: getCommentById);
    }

    private void getCommentById(RoutingContext context) {
        String productId = context.request().getParam("id");
        String sql = "SELECT * FROM comments WHERE product_id = ?";
        
        jdbcClient.queryWithParams(sql, new JsonArray().add(Integer.parseInt(productId)), result -> {
            if (result.succeeded()) {
                JsonArray comments = new JsonArray();
                result.result().getRows().forEach(comments::add);
                context.response()
                    .putHeader("Content-Type", "application/json")
                    .end(comments.encode());
            } else {
                context.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("error", result.cause().getMessage()).encode());
            }
        });
    }
    private void postComment(RoutingContext context) {
      String productId = context.request().getParam("id");
      
      JsonObject comment = context.getBodyAsJson();
      
      String sql = "INSERT INTO comments (product_id, user_name, content) VALUES (?, ?, ?)";
      
      jdbcClient.updateWithParams(sql, new JsonArray()
          .add(Integer.parseInt(productId))
          .add(comment.getString("userName"))
          .add(comment.getString("content")), // Remove the date parameter
          result -> {
              if (result.succeeded()) {
                  context.response()
                      .setStatusCode(201)
                      .putHeader("Content-Type", "application/json")
                      .end(new JsonObject().put("message", "Comment added").encode());
              } else {
                  context.response()
                      .setStatusCode(500)
                      .putHeader("Content-Type", "application/json")
                      .end(new JsonObject().put("error", "Failed to add comment").encode());
              }
          });
    }

    // Implement other methods: addProduct, updateProduct, deleteProduct
}