package com.example;

import io.vertx.core.Vertx;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.core.json.JsonObject;

public class MySQLService {
  private final JDBCClient jdbcClient;

  public MySQLService(Vertx vertx) {

    JsonObject config = new JsonObject()
        .put("url", "jdbc:mysql://localhost:3306/VERTXDATABASE?useSSL=false")
        .put("user", "root")
        .put("password", "root")
        .put("driver_class", "com.mysql.cj.jdbc.Driver")
        .put("max_pool_size", 30);

    // Initialize JDBC Client with shared config
    this.jdbcClient = JDBCClient.createShared(vertx, config);
  }

  public JDBCClient getJDBCClient() {
    return jdbcClient;
  }
}