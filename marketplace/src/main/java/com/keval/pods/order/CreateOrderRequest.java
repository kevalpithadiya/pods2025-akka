package com.keval.pods.order;
import java.util.List;

import akka.serialization.jackson.CborSerializable;

public class CreateOrderRequest implements CborSerializable {
    public Integer user_id;
    public List<OrderItemRequest> items;

    public static class OrderItemRequest {
        public Integer product_id;
        public Integer quantity;
    }
}
