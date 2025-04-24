package com.example;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLClient;

import java.util.concurrent.atomic.AtomicInteger;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;

public class CommentHandler {
    private final SQLClient jdbcClient;

    public CommentHandler(SQLClient jdbcClient) {
        this.jdbcClient = jdbcClient;
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
                System.out.println("comments"+ comments);
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
        String productId = context.request().getParam("id");
        System.out.println(productId);
        JsonObject comment = context.getBodyAsJson();
    
       
        String userId = comment.getString("id"); 
    
    
               
                String sql = "INSERT INTO comment (product_id, user_id, content) VALUES (?, ?, ?)";
                jdbcClient.updateWithParams(sql, new JsonArray()
                        .add(Integer.parseInt(productId))
                        .add(userId) // Use fetched user name
                        .add(comment.getString("content")), // Content from the request
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
        
    

}