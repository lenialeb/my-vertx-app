package com.example;
import io.vertx.core.Vertx;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.core.json.JsonObject;

/**
 * MySQLService handles the database configuration and connection pooling.
 * Configurations include database URL, user credentials, driver class, and max pool size.
 */
public class MySQLService {
  private final JDBCClient jdbcClient;

  public MySQLService(Vertx vertx) {
    // Database configuration
    JsonObject config = new JsonObject()
      .put("url", "jdbc:mysql://localhost:3306/VERTXDATABASE?useSSL=false")  // Database URL
      .put("user", "root")  // Database user
      .put("password", "root")  // No password
      .put("driver_class", "com.mysql.cj.jdbc.Driver")  // MySQL JDBC driver
      .put("max_pool_size", 30);  // Connection pool size

    // Initialize JDBC Client with shared config
    this.jdbcClient = JDBCClient.createShared(vertx, config);
  }

  public JDBCClient getJDBCClient() {
    return jdbcClient;
  }
}