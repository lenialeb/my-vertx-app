package com.example;

public class Product {
    private String id;
    private String name;
    private double price;

    // Constructors, getters, and setters
    public Product(String id, String name, double price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }
    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPrice(double price) {
        this.price = price;
    }
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
    public String getAll() {
        return name +","+ price;
    }
    public double getPrice() {
        return price;
    }
    @Override
    public String toString() {
        return "Product{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", price=" + price +
                '}';
    }
}