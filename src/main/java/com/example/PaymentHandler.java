package com.example;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.github.cdimascio.dotenv.Dotenv;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLClient;

public class PaymentHandler {

    private final SQLClient jdbcClient;
    private final Vertx vertx;
    private final String CALLBACK_URL;
    private final String FRONT_URL;
    private final String CHAPA_KEY;
    private final String SECRET_KEY;


    public PaymentHandler(SQLClient jdbcClient, Vertx vertx) {
        this.jdbcClient = jdbcClient;
        this.vertx = vertx;
        Dotenv dotenv = Dotenv.load(); // Load the .env file
  this.CALLBACK_URL = dotenv.get("CALLBACK_URL");
  System.out.println("Callback URL: " + CALLBACK_URL);
  this.FRONT_URL = dotenv.get("FRONT_URL");
  System.out.println("Front URL: " + FRONT_URL);
  this.CHAPA_KEY = dotenv.get("CHAPA_KEY");
    System.out.println("Chapa Key: " + CHAPA_KEY);
    this.SECRET_KEY = dotenv.get("SECRET_KEY");


    }

    // private static final String CALLBACK_URL = "https://localhost:4200/login";
    // private static final String FRONT_URL = "https://localhost:4200/success";
    // private static final String CHAPA_KEY = "CHASECK_TEST-Tpq7R6XJTHUlyEJaQpXcW7BPdBOwNxp3";

    public void setupRoutes(Router router) {
        router.post("/checkout").handler(this::handleCheckout);

        router.post("/payment").handler(this::handlePayment);
    }

    private void handleCheckout(RoutingContext context) {
        String token = context.request().getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            System.out.println("No valid token provided.");
            context.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(401)
                    .end(new JsonObject().put("error", "No valid token provided.").encode());
            return;
        }
        token = token.substring("Bearer ".length()).trim();
        System.out.println("Extracted token: " + token);
        if (token.split("\\.").length != 3) {
            System.out.println("Invalid token format.");
            context.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(401)
                    .end(new JsonObject().put("error", "Invalid token format.").encode());
            return;
        }
        String name;
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(SECRET_KEY)
                    .parseClaimsJws(token)
                    .getBody();
            name = claims.getSubject();
            System.out.println("Decoded username from token: " + name);
        } catch (Exception e) {
            System.out.println("Failed to decode token: " + e.getMessage());
            context.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(401)
                    .end(new JsonObject().put("error", "Invalid token.").encode());
            return;
        }
        JsonObject body = context.getBodyAsJson();
        String orderDetails = body.getString("orderDetail");
        String email = body.getString("email");
        String address = body.getString("address");
        System.out.println("Received order attempt from user: " + name);
        if (name == null || orderDetails == null || email == null || address == null) {
            System.out.println("Invalid input data from: " + name);
            context.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(400)
                    .end(new JsonObject().put("error", "Invalid input data.").encode());
            return;
        }
        String sql = "INSERT INTO orders (name, email, address, order_details) VALUES (?, ?, ?, ?)";
        JsonArray params = new JsonArray().add(name).add(email).add(address).add(orderDetails);
        jdbcClient.updateWithParams(sql, params, res -> {
            if (res.succeeded()) {
                System.out.println("Order placed successfully for user: " + name);
                context.response()
                        .putHeader("Content-Type", "application/json")
                        .setStatusCode(201)
                        .end(new JsonObject().put("message", "Order placed successfully.").encode());
                // createPaymentIntent(context, amount);
            } else {
                System.err.println("Failed to place order for user " + name + ": " + res.cause().getMessage());
                context.response()
                        .putHeader("Content-Type", "application/json")
                        .setStatusCode(500)
                        .end(new JsonObject().put("error", "Failed to place order.").encode());
            }
        });
    }
    private void handlePayment(RoutingContext ctx) {
        JsonObject paymentData = ctx.getBodyAsJson();
        String userId = paymentData.getString("id");
        Double totalAmount = paymentData.getDouble("totalAmount");
    
        System.out.println("Received amount: " + totalAmount);
        System.out.println("Received user ID: " + userId);
    
        // Validate input
        if (userId == null || totalAmount == null) {
            ctx.response().setStatusCode(400)
                    .end(new JsonObject().put("error", "User ID and total amount are required.").encode());
            return;
        }
    
        getUserById(userId, userResult -> {
            if (userResult.succeeded()) {
                JsonObject user = userResult.result();
                if (user == null) {
                    ctx.response().setStatusCode(404)
                            .end(new JsonObject().put("error", "User not found.").encode());
                    return;
                }
    
                String firstName = user.getString("name");
                String lastName = user.getString("username");
    
                processPayment(firstName, totalAmount, lastName, res -> {
                    if (res.succeeded()) {
                        JsonObject responseBody = res.result();
                        String checkoutUrl = responseBody.getJsonObject("data").getString("checkout_url");
                        ctx.response().setStatusCode(200)
                                .end(new JsonObject().put("checkoutUrl", checkoutUrl).encode());
                    } else {
                        ctx.response().setStatusCode(500)
                                .end(new JsonObject().put("error", "Payment processing failed.").encode());
                    }
                });
            } else {
                ctx.response().setStatusCode(404)
                        .end(new JsonObject().put("error", "User not found.").encode());
            }
        });
    }
    
    private void processPayment(String firstName, Double amount, String lastName,
            Handler<AsyncResult<JsonObject>> resultHandler) {
        System.out.println("Received first name: " + firstName + ", amount: " + amount + ", last name: " + lastName);
    
        JsonObject paymentRequest = new JsonObject()
                .put("email", "customer@gmail.com")
                .put("amount", amount)
                .put("currency", "ETB")
                .put("callback_url", CALLBACK_URL)
                .put("first_name", firstName)
                .put("last_name", lastName)
                .put("phone_number", "0912345678")
                .put("return_url", FRONT_URL)
                .put("tx_ref", "tx_" + System.currentTimeMillis());
    
        WebClient.create(vertx).postAbs("https://api.chapa.co/v1/transaction/initialize")
                .putHeader("Authorization", "Bearer " + CHAPA_KEY)
                .putHeader("Content-Type", "application/json")
                .sendJsonObject(paymentRequest, ar -> {
                    if (ar.succeeded()) {
                        if (ar.result().statusCode() == 200) {
                            JsonObject responseBody = ar.result().bodyAsJsonObject();
                            System.out.println("Payment successful response: " + responseBody.encodePrettily());
                            resultHandler.handle(Future.succeededFuture(responseBody));
                        } else {
                            System.out.println("Payment failed with status code: " + ar.result().statusCode());
                            System.out.println("Response body: " + ar.result().bodyAsString());
                            resultHandler.handle(Future.failedFuture("Payment processing failed with status: " + ar.result().statusCode()));
                        }
                    } else {
                        System.out.println("Error occurred while sending request to Chapa: " + ar.cause().getMessage());
                        resultHandler.handle(Future.failedFuture("Payment processing failed due to request error."));
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
