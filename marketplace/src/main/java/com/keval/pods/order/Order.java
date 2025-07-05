package com.keval.pods.order;

import java.util.List;

import akka.serialization.jackson.CborSerializable;

public class Order implements CborSerializable {
    public Integer order_id;
    public Integer user_id;
    public Integer total_price;
    public String status;
    public List<OrderItem> items;

    public static class OrderItem {
        // public Integer id;
        public Integer product_id;
        public Integer quantity;

        @Override
        public String toString() {
            return "OrderItem{" +
                    // "id=" + id +
                    "product_id=" + product_id +
                    ", quantity=" + quantity +
                    '}';
        }
    }

    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_PLACED = "PLACED";

    @Override
    public String toString() {
        return "Order{" +
                "order_id=" + order_id +
                ", user_id=" + user_id +
                ", total_price=" + total_price +
                ", status='" + status + '\'' +
                ", items=" + items +
                '}';
    }

    // Create a null order
    public Order() {
      this.order_id = null;
      this.user_id = null;
      this.total_price = null;
      this.status = null;
      this.items = null;
    }
}

