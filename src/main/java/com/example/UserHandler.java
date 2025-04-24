package com.example;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Tuple;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLClient;

import java.sql.Date;
import java.time.LocalDateTime;

import org.mindrot.jbcrypt.BCrypt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.vertx.core.json.JsonArray;
import io.github.cdimascio.dotenv.Dotenv;

public class UserHandler {

  private final SQLClient jdbcClient;
  private final String secretKey;

  public UserHandler(SQLClient jdbcClient) {
    this.jdbcClient = jdbcClient;
    Dotenv dotenv = Dotenv.load();
    this.secretKey = dotenv.get("SECRET_KEY");

  }

  public void setupRoutes(Router router) {
    router.get("/userId/:id").handler(this::getusersById);

    router.get("/users").handler(this::getUsers);
    router.get("/usersP").handler(this::getUsersPaginated);

    router.post("/users").handler(this::addUser);
    router.put("/users/:id").handler(this::updateUser);
    router.delete("/users/:id").handler(this::deleteUser);
    router.post("/login").handler(this::login);
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

  private void getUsersPaginated(RoutingContext context) {
    String authToken = context.request().getHeader("Authorization");
    if (authToken == null || !isValidToken(authToken)) {
      context.response()
          .setStatusCode(401) // Unauthorized
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("message", "Unauthorized access").encode());
      return;
    }

    String userRole = getUserRole(authToken);
    if (!"admin".equals(userRole)) {
      context.response()
          .setStatusCode(403) // Forbidden
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("message", "Access denied").encode());
      return;
    }

    String searchTerm = context.request().getParam("search", "").toLowerCase();
    int page = Integer.parseInt(context.request().getParam("page", "1"));
    int pageSize = Integer.parseInt(context.request().getParam("pageSize", "10"));
    int offset = (page - 1) * pageSize;

    String query = "SELECT * FROM user WHERE LOWER(name) LIKE ? ORDER BY name LIMIT ? OFFSET ?";
    jdbcClient.queryWithParams(query, new JsonArray().add("%" + searchTerm + "%").add(pageSize).add(offset), res -> {
      if (res.succeeded()) {
        JsonArray usersArray = new JsonArray();
        res.result().getRows().forEach(row -> {
          if (row.getValue("created_at") instanceof LocalDateTime) {
            LocalDateTime dateTime = (LocalDateTime) row.getValue("created_at");
            row.put("created_at", dateTime.toString());
          }
          usersArray.add(row);
        });

        jdbcClient.queryWithParams("SELECT COUNT(*) AS total FROM user WHERE LOWER(name) LIKE ?",
            new JsonArray().add("%" + searchTerm + "%"), countRes -> {
              if (countRes.succeeded()) {
                int total = countRes.result().getRows().get(0).getInteger("total");
                JsonObject response = new JsonObject()
                    .put("users", usersArray)
                    .put("total", total)
                    .put("page", page)
                    .put("pageSize", pageSize);
                context.response()
                    .putHeader("Content-Type", "application/json")
                    .end(response.encodePrettily());
              } else {
                context.response()
                    .setStatusCode(500)
                    .end("Error counting users: " + countRes.cause().getMessage());
              }
            });
      } else {
        context.response()
            .setStatusCode(500)
            .end("Error retrieving users: " + res.cause().getMessage());
      }
    });
  }

  private boolean isValidToken(String token) {
    if (token == null || !token.startsWith("Bearer ")) {
      return false;
    }
    token = token.substring(7);
    try {
      Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private String getUserRole(String token) {
    try {
      Claims claims = Jwts.parser()
          .setSigningKey(secretKey)
          .parseClaimsJws(token.substring(7)) // Remove "Bearer " prefix
          .getBody();
      return claims.get("role", String.class);
    } catch (Exception e) {
      return null;
    }
  }

  private void updateUser(RoutingContext context) {
    String id = context.pathParam("id");
    JsonObject body = context.getBodyAsJson();
    executeQuery("UPDATE user SET name = ?, username = ?, password = ? WHERE id = ?",
        new JsonArray().add(body.getString("name")).add(body.getString("username")).add(body.getString("password"))
            .add(id),
        context, "User updated");
  }

  private void deleteUser(RoutingContext context) {
    String id = context.pathParam("id");
    executeQuery("DELETE FROM user WHERE id = ?",
        new JsonArray().add(id),
        context, "User deleted");
  }

 
  private void addUser(RoutingContext context) {
    JsonObject body = context.getBodyAsJson();
    String hashedPassword = BCrypt.hashpw(body.getString("password"), BCrypt.gensalt());

    final String role = body.getString("role") == null ? "user" : body.getString("role");
    String checkUsernameSql = "SELECT COUNT(*) AS count FROM user WHERE username = ?";
    jdbcClient.queryWithParams(checkUsernameSql, new JsonArray().add(body.getString("username")), checkResult -> {
      if (checkResult.succeeded()) {
        int count = checkResult.result().getRows().get(0).getInteger("count");
        if (count > 0) {
         
          context.response()
              .setStatusCode(400) 
              .putHeader("Content-Type", "application/json")
              .end(new JsonObject().put("message", "Username already exists").encode());
          return;
        }

        executeQuery("INSERT INTO user (name, username, password, role) VALUES (?, ?, ?, ?)",
            new JsonArray()
                .add(body.getString("name"))
                .add(body.getString("username"))
                .add(hashedPassword)
                .add(role), 
            context, "User added");

        context.response()
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("message", "Registered successfully").encode());
      } else {
        context.response()
            .setStatusCode(500)
            .end(new JsonObject().put("error", checkResult.cause().getMessage()).encode());
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

  private void login(RoutingContext context) {

    JsonObject body = context.getBodyAsJson();
    String username = body.getString("username");
    String password = body.getString("password");

    jdbcClient.queryWithParams("SELECT * FROM user WHERE username = ?", new JsonArray().add(username), res -> {
      if (res.succeeded()) {
        if (res.result().getNumRows() > 0) {
          JsonObject user = res.result().getRows().get(0);
          String hashedPassword = user.getString("password");

          if (BCrypt.checkpw(password, hashedPassword)) {
            String name = user.getString("name");
            String id = user.getString("id");
            String role = user.getString("role");
            String token = Jwts.builder()
                .setSubject(username)
                .claim("name", name)
                .claim("id", id)
                .claim("role", role)
                .setIssuedAt(new java.util.Date())
                .setExpiration(new Date(System.currentTimeMillis() + 86400000)) // 1 day expiration
                .signWith(SignatureAlgorithm.HS256, secretKey) // Use HS256 algorithm
                .compact();

            // Create the response JSON object
            JsonObject responseJson = new JsonObject()
                .put("message", "Login successful")
                .put("token", token)
                .put("user", new JsonObject()
                    .put("id", id)
                    .put("username", username)
                    .put("name", name)
                    .put("role", role)); // Move role inside user object

            // Send the response
            context.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(responseJson.encode());
          } else {
            context.response()
                .setStatusCode(401)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Invalid credentials").encode());
          }
        } else {
          context.response()
              .setStatusCode(401)
              .putHeader("Content-Type", "application/json")
              .end(new JsonObject().put("error", "Invalid credentials").encode());
        }
      } else {
        context.response()
            .setStatusCode(500)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("error", "Internal server error").encode());
      }
    });
  }

}