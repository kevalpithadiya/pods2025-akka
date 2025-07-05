package com.keval.pods.order;

import akka.serialization.jackson.CborSerializable;

public class UpdateOrderRequest implements CborSerializable {
    public Integer order_id;
    public String status;
}
