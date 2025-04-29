package com.example;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLClient;


import io.github.cdimascio.dotenv.Dotenv;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;

public class CommentHandler {
  private final SQLClient jdbcClient;
  private final String secretKey;

  public CommentHandler(SQLClient jdbcClient) {
    this.jdbcClient = jdbcClient;
    Dotenv dotenv = Dotenv.load();
    this.secretKey = dotenv.get("SECRET_KEY");
  }

  public void setupRoutes(Router router) {
    router.post("/comment/:id").handler(this::postComment);
    router.get("/comment/:id").handler(this::getCommentById);
  }

  private void getCommentById(RoutingContext context) {
    String productId = context.request().getParam("id");

    String sql = "SELECT c.*, u.name AS userName FROM comment c " +
        "JOIN user u ON c.user_id = u.id " +
        "WHERE c.product_id = ?";

    jdbcClient.queryWithParams(sql, new JsonArray().add(Integer.parseInt(productId)), result -> {
      if (result.succeeded()) {
        JsonArray comments = new JsonArray(result.result().getRows());
        System.out.println("comments" + comments);
        int totalCount = comments.size();

        context.response()
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject()
                .put("total_comments", totalCount)
                .put("comments", comments)
                .encode());
      } else {

        System.err.println("Error fetching comments: " + result.cause().getMessage());
        context.response()
            .setStatusCode(500)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("error", result.cause().getMessage()).encode());
      }
    });
  }

  private void postComment(RoutingContext context) {
    String authToken = context.request().getHeader("Authorization");

    System.out.println("token" + authToken);
    JsonObject body = context.getBodyAsJson();
    String productId = context.request().getParam("id");
    String comment = body.getString("content");
    String userId = getUserId(authToken);

    String sql = "INSERT INTO comment (product_id, user_id, content) VALUES (?, ?, ?)";
    jdbcClient.updateWithParams(sql, new JsonArray()
        .add(Integer.parseInt(productId))
        .add(userId)
        .add(comment),
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

  private String getUserId(String token) {
    try {
      Claims claims = Jwts.parser()
          .setSigningKey(secretKey)
          .parseClaimsJws(token.substring(7)) // Remove "Bearer " prefix
          .getBody();
      return claims.get("id", String.class);
    } catch (Exception e) {
      return null;
    }
  }
  // private boolean isValidToken(String token) {
  // if (token == null || !token.startsWith("Bearer ")) {
  // return false;
  // }
  // token = token.substring(7);
  // try {
  // Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token);
  // return true;
  // } catch (Exception e) {
  // return false;
  // }
  // }
}