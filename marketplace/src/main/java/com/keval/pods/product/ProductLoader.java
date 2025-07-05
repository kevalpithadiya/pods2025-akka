package com.keval.pods.product;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.opencsv.CSVReader;

public class ProductLoader {

    // Loading products from CSV file
    // Map to hold products
    public final List<Integer> productIds = new ArrayList<>();
    public final Map<Integer, Product> products = new HashMap<>();

    public void loadProductsFromCsv() {
        try (CSVReader reader = new CSVReader(new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("products.csv")))) {

            reader.readNext(); // Skip header
            String[] parts;
            while ((parts = reader.readNext()) != null) {
                int id = Integer.parseInt(parts[0].trim());
                String name = parts[1].trim();
                String description = parts[2].trim();
                int price = Integer.parseInt(parts[3].trim());
                int stock_quantity = Integer.parseInt(parts[4].trim());

                Product product = new Product(id, name, description, price, stock_quantity);
                productIds.add(id);
                products.put(id, product);
            }
        } catch (Exception e) {
            System.err.println("Error loading products from CSV: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
}
