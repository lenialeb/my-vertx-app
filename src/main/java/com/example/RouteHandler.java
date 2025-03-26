package com.example;
import java.security.PrivateKey;
import java.sql.Date;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;

import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.core.http.HttpMethod;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.vertx.core.Vertx;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.handler.BodyHandler;
/**
 * RouteHandler sets up routes and handles requests.
 */
public class RouteHandler {
  private final JDBCClient jdbcClient;

  public RouteHandler(Vertx vertx, MySQLService mySQLService) {
    this.jdbcClient = mySQLService.getJDBCClient();
  }
  

  public Router setupRoutes(Vertx vertx) {
    Router router = Router.router(vertx);
    router.route().handler(CorsHandler.create() // Allow all origins
        .addOrigin("*")
        .allowedMethods(new HashSet<>(Arrays.asList(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.OPTIONS)))
        .allowedHeaders(new HashSet<>(Arrays.asList("Content-Type", "Authorization")))
        .exposedHeaders(new HashSet<>(Arrays.asList("Authorization")))); // Optionally expose headers
    router.route().handler(BodyHandler.create()); // Move this line here
    router.get("/products").handler(this::getProducts);
    router.post("/products").handler(this::addProduct);
    router.put("/products/:id").handler(this::updateProduct);
    router.get("/productId/:id").handler(this::getProductsById);

    router.delete("/products/:id").handler(this::deleteProduct);
    router.get("/users").handler(this::getUsers);
    router.post("/users").handler(this::addUser);
    router.put("/users/:id").handler(this::updateUser);
    router.delete("/users/:id").handler(this::deleteUser);
    router.post("/login").handler(this::login);
    

    return router;
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
  private void addProduct(RoutingContext context) {
    JsonObject body = context.getBodyAsJson();
    executeQuery("INSERT INTO products (name, price) VALUES (?, ?)",
                 new JsonArray().add(body.getString("name")).add(body.getDouble("price")),
                 context, "Product added");
  }

  private void updateProduct(RoutingContext context) {
    String id = context.pathParam("id");
    JsonObject body = context.getBodyAsJson();
    executeQuery("UPDATE products SET name = ?, price = ? WHERE id = ?",
                 new JsonArray().add(body.getString("name")).add(body.getDouble("price")).add(id),
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
                // Convert LocalDateTime to String if necessary
                if (row.getValue("created_at") instanceof LocalDateTime) {
                    LocalDateTime dateTime = (LocalDateTime) row.getValue("created_at");
                    row.put("created_at", dateTime.toString()); // Convert to ISO-8601 string
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

  // private void register(RoutingContext context) {
  //   JsonObject body = context.getBodyAsJson();
  //   executeQuery("INSERT INTO user (name, username, password) VALUES (?, ?, ?)",
  //                new JsonArray().add(body.getString("name")).add(body.getString("username")).add(body.getString("password")),
  //                context, "User registered");
  // }
  // private void login(RoutingContext context) {
  //   JsonObject body = context.getBodyAsJson();
  //   String username = body.getString("username");
  //   String password = body.getString("password");
  
  //   jdbcClient.queryWithParams("SELECT * FROM user WHERE username = ? AND password = ?", 
  //                              new JsonArray().add(username).add(password), res -> {
  //       if (res.succeeded() && res.result().getNumRows() > 0) {
  //           // Generate JWT
  //           String token = Jwts.builder()
  //                   .setSubject(username)
  //                   .setIssuedAt(new Date())
  //                   .setExpiration(new Date(System.currentTimeMillis() + 86400000)) // 1 day expiration
  //                   .signWith(SignatureAlgorithm.HS256, "your-secret-key") // Use a strong secret key
  //                   .compact();
  
  //           context.response()
  //                  .putHeader("Content-Type", "application/json")
  //                  .end(new JsonObject().put("token", token).put("message", "Login successful").encode());
  //       } else {
  //           context.response()
  //                  .setStatusCode(401)
  //                  .putHeader("Content-Type", "application/json")
  //                  .end(new JsonObject().put("error", "Invalid credentials").encode());
  //       }
  //   });
  // }

 
  private void login(RoutingContext context) {
    JsonObject body = context.getBodyAsJson();
    String username = body.getString("username");
    String password = body.getString("password");

    System.out.println("Received login attempt for username: " + username); // Log the username

    jdbcClient.queryWithParams("SELECT * FROM user WHERE username = ? AND password = ?", 
                               new JsonArray().add(username).add(password), res -> {
        if (res.succeeded()) {
            System.out.println("Query succeeded"); // Log query success
            if (res.result().getNumRows() > 0) {
                // Assuming the user table has the columns "full_name" and "email"
                JsonObject user = res.result().getRows().get(0); // Get the first (and presumably only) user
                String name = user.getString("name"); // Adjust based on your column name
                 // Optional: include other user info

                // Create the JWT with user info
                String token = Jwts.builder()
                       .setSubject(username)
                       .claim("name", name) // Include the full name in the token
                      // Optionally include the email
                       .setIssuedAt(new java.util.Date())
                       .setExpiration(new Date(System.currentTimeMillis() + 86400000)) // 1 day expiration
                       .signWith(SignatureAlgorithm.HS256, "your-secret-key")
                       .compact();

                System.out.println("Login successful, token generated for: " + username); // Log success
                context.response()
                       .setStatusCode(200) // Ensure status is set to 200
                       .putHeader("Content-Type", "application/json")
                       .end(new JsonObject()
                           .put("message", "Login successful")
                           .put("token", token)
                           .put("user", new JsonObject()
                               .put("username", username)
                               .put("name", name)
                               ) // Include user's full info in the response
                           .encode());
            } else {
                // Invalid credentials
                System.out.println("Invalid credentials for: " + username); // Log invalid credentials
                context.response()
                       .setStatusCode(401)
                       .putHeader("Content-Type", "application/json")
                       .end(new JsonObject().put("error", "Invalid credentials").encode());
            }
        } else {
            // Handle query failure
            System.out.println("Query failed: " + res.cause().getMessage()); // Log query failure
            context.response()
                   .setStatusCode(500)
                   .putHeader("Content-Type", "application/json")
                   .end(new JsonObject().put("error", "Internal server error").encode());
        }
    });
}
}
  