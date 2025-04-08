package com.example;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLClient;

import java.sql.Date;
import java.time.LocalDateTime;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.vertx.core.json.JsonArray;

public class UserHandler {
    private final SQLClient jdbcClient;

    public UserHandler(SQLClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void setupRoutes(Router router) {
        router.get("/userId/:id").handler(this::getusersById);
   
        router.get("/users").handler(this::getUsers);
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
    // private void getUsers(RoutingContext context) {
    //     jdbcClient.query("SELECT * FROM user", res -> {
    //         if (res.succeeded()) {
    //             context.response().end(new JsonArray(res.result().getRows()).encodePrettily());
    //         } else {
    //             context.response().setStatusCode(500).end(res.cause().getMessage());
    //         }
    //     });
    // }
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
private void addUser(RoutingContext context) {
    JsonObject body = context.getBodyAsJson();
    executeQuery("INSERT INTO user (name, username, password) VALUES (?, ?, ?)",
                 new JsonArray().add(body.getString("name")).add(body.getString("username")).add(body.getString("password")),
                 context, "User added");
                 context.response()
                   .putHeader("Content-Type", "application/json")
                   .end(new JsonObject().put("message", "Registered successful").encode());
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
   

    // Implement other methods: addUser, updateUser, deleteUser, login
}