package com.example;
import io.github.cdimascio.dotenv.Dotenv;
import io.vertx.core.Vertx;

public class App 
{
    public static void main( String[] args )
    { 
        Dotenv dotenv = Dotenv.load();
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new MainVerticle());
    }
}
