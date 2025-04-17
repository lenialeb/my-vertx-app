package com.example;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLClient;
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
        String sql = "SELECT * FROM comments WHERE product_id = ?";
    
        jdbcClient.queryWithParams(sql, new JsonArray().add(Integer.parseInt(productId)), result -> {
            if (result.succeeded()) {
                JsonArray comments = new JsonArray();
                result.result().getRows().forEach(comments::add);
    
                // Query to count the total number of comments for the product
                String countSql = "SELECT COUNT(*) AS total FROM comments WHERE product_id = ?";
                jdbcClient.queryWithParams(countSql, new JsonArray().add(Integer.parseInt(productId)), countResult -> {
                    if (countResult.succeeded()) {
                        int totalCount = countResult.result().getRows().get(0).getInteger("total");
    
                        // Combine comments and total count into a response object
                        JsonObject response = new JsonObject()
                                .put("total_comments", totalCount)
                                .put("comments", comments);
    
                        context.response()
                                .putHeader("Content-Type", "application/json")
                                .end(response.encode());
                    } else {
                        context.response()
                                .setStatusCode(500)
                                .putHeader("Content-Type", "application/json")
                                .end(new JsonObject().put("error", countResult.cause().getMessage()).encode());
                    }
                });
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
    
        // Fetch the user ID from the incoming comment JSON
        String userId = comment.getString("id"); // Assuming 'id' is the user ID
    
        getUserById(userId, userResult -> {
            if (userResult.succeeded()) {
                JsonObject user = userResult.result();
                if (user == null) {
                    context.response().setStatusCode(404)
                            .end(new JsonObject().put("error", "User not found.").encode());
                    return;
                }
    
                // Get the user's name
                String userName = user.getString("name");
    
                // Prepare the SQL for inserting the comment
                String sql = "INSERT INTO comments (product_id, user_name, content) VALUES (?, ?, ?)";
                jdbcClient.updateWithParams(sql, new JsonArray()
                        .add(Integer.parseInt(productId))
                        .add(userName) // Use fetched user name
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
            } else {
                // Handle error if user query fails
                context.response().setStatusCode(500)
                        .end(new JsonObject().put("error", "Failed to retrieve user information").encode());
            }
        });
    }
   private void getUserById(String userId, Handler<AsyncResult<JsonObject>> resultHandler) {
        jdbcClient.queryWithParams("SELECT * FROM user WHERE id = ?", new JsonArray().add(userId), res -> {
            if (res.succeeded() && res.result().getRows().size() > 0) {

                JsonObject user = res.result().getRows().get(0);
                resultHandler.handle(Future.succeededFuture(user));
            } else {
                resultHandler.handle(Future.failedFuture("User not found."));
            }
        });
    }

}