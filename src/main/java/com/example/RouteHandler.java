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
  private final JDBCClient jdbcClient;
 private SQLClient sqlClient;
  private final Vertx vertx;

  public RouteHandler(Vertx vertx, MySQLService mySQLService) {
    this.vertx = vertx;
    this.jdbcClient = mySQLService.getJDBCClient();
  }
  
    private static final String CALLBACK_URL= "https://localhost:4200/login";
    
    private static final String FRONT_URL = "https://localhost:4200/success";
    private static final String CHAPA_KEY = "CHASECK_TEST-Tpq7R6XJTHUlyEJaQpXcW7BPdBOwNxp3";

  public Router setupRoutes(Vertx vertx) {
    Router router = Router.router(vertx);
  
    router.route().handler(CorsHandler.create() 
        .addOrigin("*")
        .allowedMethods(new HashSet<>(Arrays.asList(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.OPTIONS)))
        .allowedHeaders(new HashSet<>(Arrays.asList("Content-Type", "Authorization")))
        .exposedHeaders(new HashSet<>(Arrays.asList("Authorization")))); 
    router.route().handler(BodyHandler.create());
    router.get("/products").handler(this::getProducts);
    router.post("/products").handler(this::addProduct);
    router.put("/products/:id").handler(this::updateProduct);
    router.get("/productId/:id").handler(this::getProductsById);
    router.get("/userId/:id").handler(this::getusersById);
    router.delete("/products/:id").handler(this::deleteProduct);
    router.get("/users").handler(this::getUsers);
    router.post("/users").handler(this::addUser);
    router.put("/users/:id").handler(this::updateUser);
    router.delete("/users/:id").handler(this::deleteUser);
    router.post("/login").handler(this::login);
    router.post("/checkout").handler(this::handleCheckout);
    router.get("/orders").handler(this::handleGetOrders);
    router.put("/updateStatus/:id").handler(this::handleUpdateOrderStatus);
    router.post("/payment").handler(this:: handlePayment);
    return router;
  }
  
  private void handlePayment(RoutingContext ctx) {
    JsonObject paymentData = ctx.getBodyAsJson();
    String userId = paymentData.getString("id");
    Double totalAmount = paymentData.getDouble("totalAmount");
    System.out.println("Received amount: " + totalAmount);
    System.out.println("Received user ID: " + userId);
    if (userId == null || totalAmount == null) {
        ctx.response().setStatusCode(400).end(new JsonObject().put("error", "User ID and total amount are required.").encode());
        return;
    }
    getUserById(userId, userResult -> {
        if (userResult.succeeded()) {
            JsonObject user = userResult.result();
            // String email = user.getString("email");
            String firstName = user.getString("name");
            String lastName = user.getString("username");
            // String phoneNumber = user.getString("phone_number");
            processPayment(firstName, totalAmount,lastName, res -> {
                if (res.succeeded()) {
                    JsonObject responseBody = res.result();
                    String checkoutUrl = responseBody.getJsonObject("data").getString("checkout_url");
                    ctx.response().setStatusCode(200).end(new JsonObject().put("checkoutUrl", checkoutUrl).encode());
                } else {
                    ctx.response().setStatusCode(500).end(new JsonObject().put("error", "Payment processing failed.").encode());
                }
            });
        } else {
            ctx.response().setStatusCode(404).end(new JsonObject().put("error", "User not found.").encode());
        }
    });
}

    private void processPayment(String firstName, Double amount,String lastName, Handler<AsyncResult<JsonObject>> resultHandler) {
    System.out.println(" received firstname from payment: " + firstName +"received amount"+ amount + "received lastname"+lastName);
        JsonObject paymentRequest = new JsonObject()
        .put("email", "customer@gmail.com")
        .put("amount", amount)
        .put("currency", "ETB")
        .put("callback_url", CALLBACK_URL)
        .put("first_name", firstName)
        .put("last_name", lastName)
        .put("phone_number", "0912345678")
        .put("return_url", FRONT_URL)
        .put("tx_ref", "tx_" + System.currentTimeMillis()) ;
      
        WebClient.create(vertx).postAbs("https://api.chapa.co/v1/transaction/initialize")
        .putHeader("Authorization", "Bearer " + CHAPA_KEY) // Ensure there's a space after "Bearer"
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
                    resultHandler.handle(Future.failedFuture("Payment processing failed."));
                }
            } else {
                System.out.println("Error occurred while sending request to Chapa: " + ar.cause().getMessage());
                resultHandler.handle(Future.failedFuture("Payment processing failed."));
            }
        });}
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
              .setSigningKey("your-secret-key") 
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
  if  (name == null || orderDetails == null || email == null || address == null) {
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
                //  createPaymentIntent(context, amount);
      } else {
          System.err.println("Failed to place order for user " + name + ": " + res.cause().getMessage());
          context.response()
                 .putHeader("Content-Type", "application/json")
                 .setStatusCode(500) 
                 .end(new JsonObject().put("error", "Failed to place order.").encode());
      }
  });
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
  private void executeQuery(String query, JsonArray params, RoutingContext context, String successMessage) {
    jdbcClient.updateWithParams(query, params, res -> {
      if (res.succeeded()) {
        context.response().end(successMessage);
      } else {
        context.response().setStatusCode(500).end(res.cause().getMessage());
      }
    });
  }
  private void getProducts(RoutingContext context) {
    jdbcClient.query("SELECT * FROM products", res -> {
      if (res.succeeded()) {
        context.response().end(new JsonArray(res.result().getRows()).encodePrettily());
      } else {
        context.response().setStatusCode(500).end(res.cause().getMessage());
      }
    });
  }
private void getProductsById(RoutingContext context) {
    String id = context.pathParam("id");
    jdbcClient.queryWithParams("SELECT * FROM products WHERE id = ?", new JsonArray().add(id), res -> {
      if (res.succeeded()) {
        context.response().end(new JsonArray(res.result().getRows()).encodePrettily());
      } else {
        context.response().setStatusCode(500).end(res.cause().getMessage());
      }
    });
  }
 private void getusersById(RoutingContext context) {
    String id = context.pathParam("id");
    jdbcClient.queryWithParams("SELECT * FROM user WHERE id = ?", new JsonArray().add(id), res -> {
      if (res.succeeded()) {
        context.response().end(new JsonArray(res.result().getRows()).encodePrettily());
      } else {
        context.response().setStatusCode(500).end(res.cause().getMessage());
      }
    });
  }

  private void addProduct(RoutingContext context) {
    JsonObject body = context.getBodyAsJson();
    executeQuery("INSERT INTO products (name, price, description, image) VALUES (?, ?,?,?)",
                 new JsonArray().add(body.getString("name")).add(body.getDouble("price")).add(body.getString("description")).add(body.getString("image")),
                 context, "Product added");
  }

  private void updateProduct(RoutingContext context) {
    String id = context.pathParam("id");
    JsonObject body = context.getBodyAsJson();
    executeQuery("UPDATE products SET name = ?, price = ?, description = ?, image = ?  WHERE id = ?",
                 new JsonArray().add(body.getString("name")).add(body.getDouble("price")).add(body.getString("description")).add(body.getString("image")).add(id),
                 context, "Product updated");
  }

  private void deleteProduct(RoutingContext context) {
    String id = context.pathParam("id");
    executeQuery("DELETE FROM products WHERE id = ?",
                 new JsonArray().add(id),
                 context, "Product deleted");
  }
  private void getUsers(RoutingContext context) {
    jdbcClient.query("SELECT * FROM user", res -> {
        if (res.succeeded()) {
            JsonArray usersArray = new JsonArray();
            res.result().getRows().forEach(row -> {
                
                if (row.getValue("created_at") instanceof LocalDateTime) {
                    LocalDateTime dateTime = (LocalDateTime) row.getValue("created_at");
                    row.put("created_at", dateTime.toString()); 
                }
                usersArray.add(row);
            });

            context.response()
                .putHeader("Content-Type", "application/json")
                .end(usersArray.encodePrettily());
        } else {
            context.response()
                .setStatusCode(500)
                .end("Error retrieving users: " + res.cause().getMessage());
        }
    });
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
  private void addUser(RoutingContext context) {
    JsonObject body = context.getBodyAsJson();
    executeQuery("INSERT INTO user (name, username, password) VALUES (?, ?, ?)",
                 new JsonArray().add(body.getString("name")).add(body.getString("username")).add(body.getString("password")),
                 context, "User added");
                 context.response()
                   .putHeader("Content-Type", "application/json")
                   .end(new JsonObject().put("message", "Registered successful").encode());
  }

  private void updateUser(RoutingContext context) {
    String id = context.pathParam("id");
    JsonObject body = context.getBodyAsJson();
    executeQuery("UPDATE user SET name = ?, username = ?, password = ? WHERE id = ?",
                 new JsonArray().add(body.getString("name")).add(body.getString("username")).add(body.getString("password")).add(id),
                 context, "User updated");
  }
  private void deleteUser(RoutingContext context) {
    String id = context.pathParam("id");
    executeQuery("DELETE FROM user WHERE id = ?",
                 new JsonArray().add(id),
                 context, "User deleted");
  }
  private void login(RoutingContext context) {
    JsonObject body = context.getBodyAsJson();
    String username = body.getString("username");
    String password = body.getString("password");
    System.out.println("Received login attempt for username: " + username); 
    jdbcClient.queryWithParams("SELECT * FROM user WHERE username = ? AND password = ?", 
                               new JsonArray().add(username).add(password), res -> {
        if (res.succeeded()) {
            System.out.println("Query succeeded");
            if (res.result().getNumRows() > 0) {
                JsonObject user = res.result().getRows().get(0); 
                String name = user.getString("name"); 
                String id = user.getString("id"); 
                String token = Jwts.builder()
                       .setSubject(username)
                       .claim("name", name) 
                       .claim("id", id)

                       .setIssuedAt(new java.util.Date())
                       .setExpiration(new Date(System.currentTimeMillis() + 86400000)) 
                       .signWith(SignatureAlgorithm.HS256, "your-secret-key")
                       .compact();

                System.out.println("Login successful, token generated for: " + username + id); 
                context.response()
                       .setStatusCode(200) 
                       .putHeader("Content-Type", "application/json")
                       .end(new JsonObject()
                           .put("message", "Login successful")
                           .put("token", token)
                           .put("user", new JsonObject()
                           .put("id",id)
                               .put("username", username)
                               .put("name", name)
                               ) 
                           .encode());
            } else {
              
                System.out.println("Invalid credentials for: " + username);
                context.response()
                       .setStatusCode(401)
                       .putHeader("Content-Type", "application/json")
                       .end(new JsonObject().put("error", "Invalid credentials").encode());
            }
        } else {
           
            System.out.println("Query failed: " + res.cause().getMessage()); 
            context.response()
                   .setStatusCode(500)
                   .putHeader("Content-Type", "application/json")
                   .end(new JsonObject().put("error", "Internal server error").encode());
        }
    });
}


}
  