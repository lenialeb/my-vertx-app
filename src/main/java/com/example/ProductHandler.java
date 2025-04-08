package com.example;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLClient;
import io.vertx.core.json.JsonArray;

public class ProductHandler {
    private final SQLClient jdbcClient;

    public ProductHandler(SQLClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void setupRoutes(Router router) {
        router.get("/products").handler(this::getProducts);
        router.post("/products").handler(this::addProduct);
        router.put("/products/:id").handler(this::updateProduct);
        router.get("/productId/:id").handler(this::getProductsById);
        router.get("/productCategory/:category").handler(this::getProductsByCategoy);
        router.delete("/products/:id").handler(this::deleteProduct);
    }
    private void getProductsByCategoy(RoutingContext context) {
        String category = context.pathParam("category");
        jdbcClient.queryWithParams("SELECT * FROM products WHERE category = ? ", new JsonArray().add(category), res -> {
          if (res.succeeded()) {
            context.response().end(new JsonArray(res.result().getRows()).encodePrettily());
          } else {
            context.response().setStatusCode(500).end(res.cause().getMessage());
          }
        });
      }
    
    
      private void addProduct(RoutingContext context) {
        JsonObject body = context.getBodyAsJson();
        executeQuery("INSERT INTO products (name, price, description,category, image) VALUES (?, ?,?,?,?)",
                     new JsonArray().add(body.getString("name")).add(body.getDouble("price")).add(body.getString("description")).add(body.getString("category")).add(body.getString("image")),
                     context, "Product added");
      }
    
      private void updateProduct(RoutingContext context) {
        String id = context.pathParam("id");
        JsonObject body = context.getBodyAsJson();
        executeQuery("UPDATE products SET name = ?, price = ?, description = ?,category = ?, image = ?  WHERE id = ?",
                     new JsonArray().add(body.getString("name")).add(body.getDouble("price")).add(body.getString("description")).add(body.getString("category")).add(body.getString("image")).add(id),
                     context, "Product updated");
      }
    
      private void deleteProduct(RoutingContext context) {
        String id = context.pathParam("id");
        executeQuery("DELETE FROM products WHERE id = ?",
                     new JsonArray().add(id),
                     context, "Product deleted");
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

    // Implement other methods: addProduct, updateProduct, deleteProduct
}