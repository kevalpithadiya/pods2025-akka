package com.keval.pods.product;

import akka.serialization.jackson.CborSerializable;

public class Product implements CborSerializable {
    public final Integer id;
    public final String name;
    public final String description;
    public final Integer price;
    public Integer stock_quantity;

    public Product(Integer id, String name, String description, Integer price, Integer stock_quantity) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.description = description;
        this.stock_quantity = stock_quantity;
    }

    // Create a null product
    public Product() {
        this.id = null;
        this.name = null;
        this.price = null;
        this.description = null;
        this.stock_quantity = null;
    }
}
