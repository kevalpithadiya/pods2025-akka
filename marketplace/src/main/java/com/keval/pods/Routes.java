package com.keval.pods;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.AskPattern;
import akka.http.javadsl.marshallers.jackson.Jackson;

import static akka.http.javadsl.server.Directives.*;

import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.Route;

import com.keval.pods.order.CreateOrderRequest;
import com.keval.pods.order.Order;
import com.keval.pods.order.UpdateOrderRequest;
import com.keval.pods.order.Order.OrderItem;
import com.keval.pods.product.Product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Routes {

    private final static Logger log = LoggerFactory.getLogger(Routes.class);

    private final ActorRef<Gateway.Command> gatewayActor;
    private final Duration askTimeout;
    private final Scheduler scheduler;

    public Routes(ActorSystem<?> system, ActorRef<Gateway.Command> gatewayActor) {
        this.gatewayActor = gatewayActor;
        this.scheduler = system.scheduler();
        this.askTimeout = system.settings().config().getDuration("marketplace-app.routes.ask-timeout");
    }

    private CompletionStage<Product> getProduct(int productId) {
        return AskPattern.ask(gatewayActor, ref -> new Gateway.GetProduct(productId, ref), askTimeout, scheduler);
    }

    private CompletionStage<Order> createOrder(CreateOrderRequest orderRequest) {
        Order order = convertCreateOrderRequestToOrder(orderRequest);
        return AskPattern.ask(gatewayActor, ref -> new Gateway.CreateOrder(order, ref), askTimeout, scheduler);
    }

    private CompletionStage<Order> getOrder(int orderId) {
        return AskPattern.ask(gatewayActor, ref -> new Gateway.GetOrder(orderId, ref), askTimeout, scheduler);
    }

    private CompletionStage<Boolean> deleteOrder(int orderId) {
        //print
        System.out.println("Deleting order with ID: " + orderId);
        return AskPattern.ask(gatewayActor, ref -> new Gateway.CancelOrder(orderId, ref), askTimeout, scheduler);
    }

    private CompletionStage<Boolean> updateOrder(Integer orderId, UpdateOrderRequest orderRequest) {
        Order order = convertUpdateOrderRequestToOrder(orderRequest);
        return AskPattern.ask(gatewayActor, ref -> new Gateway.UpdateOrder(orderId, order, ref), askTimeout, scheduler);
    }

    // Utitility function: Concert CreateOrderRequest -> Order
    private Order convertCreateOrderRequestToOrder(CreateOrderRequest orderRequest) {
        Order order = new Order();
        if (orderRequest != null) {
            order.user_id = orderRequest.user_id;
            if (orderRequest.items != null) {
                List<OrderItem> orderItems = new ArrayList<>();
                for (CreateOrderRequest.OrderItemRequest itemRequest : orderRequest.items) {
                    OrderItem item = new OrderItem();
                    item.product_id = itemRequest.product_id;
                    item.quantity = itemRequest.quantity;
                    orderItems.add(item);
                }
                order.items = orderItems;
            }
        }
        return order;
    }

    // Utitility function: Concert UpdateOrderRequest -> Order
    private Order convertUpdateOrderRequestToOrder(UpdateOrderRequest orderRequest) {
        Order order = new Order();
        if (orderRequest != null) {
            order.order_id = orderRequest.order_id;
            order.status = orderRequest.status;
        }
        return order;
    }

    public Route userRoutes() {
        return concat(
                // GET /products/{productId}
                pathPrefix("products", () -> concat(
                        path(PathMatchers.segment(), (String id) -> get(() -> {
                            Integer productId = Integer.parseInt(id);
                            return onSuccess(getProduct(productId), product -> {
                                if (product.id != null) {
                                    return complete(StatusCodes.OK, product, Jackson.marshaller());
                                } else {
                                    return complete(StatusCodes.NOT_FOUND, "Product not found.");
                                }
                            });
                        })))),
                pathPrefix("orders", () -> concat(
                        // POST /orders
                        post(() -> entity(Jackson.unmarshaller(CreateOrderRequest.class),
                                orderRequest -> onSuccess(createOrder(orderRequest), order -> {
                                    if (order.order_id != null) {
                                        log.info("Order created: {}", order);
                                        return complete(StatusCodes.CREATED, order, Jackson.marshaller());
                                    }
                                    else {
                                        log.info("Order creation failed: {}", orderRequest);
                                        return complete(StatusCodes.BAD_REQUEST, "Order creation failed");
                                    }
                                }))),
                        path(PathMatchers.segment(), (String id) -> concat(
                                // GET /orders/{orderId}
                                get(() -> {
                                    int orderId = Integer.parseInt(id);
                                    return onSuccess(getOrder(orderId), order -> {
                                        if (order.order_id != null) {
                                            return complete(StatusCodes.OK, order, Jackson.marshaller());
                                        } else {
                                            return complete(StatusCodes.NOT_FOUND, "Order not found");
                                        }
                                    });
                                }),
                                // DELETE /orders/{orderId}
                                delete(() -> {
                                    int orderId = Integer.parseInt(id);
                                    return onSuccess(deleteOrder(orderId), successful -> {
                                            if (successful){
                                              System.out.println("Order deleted: "+ orderId+ ", Returning 200");
                                              return complete(StatusCodes.OK);
                                            }
                                            else{
                                              // return 400
                                              System.out.println("Order deletion failed: "+ orderId+ ", Retuning 400"); 
                                              return complete(StatusCodes.BAD_REQUEST);
                                            }
                                    });
                                }),
                                // PUT /orders/{orderID}
                                put(() -> entity(Jackson.unmarshaller(UpdateOrderRequest.class),
                                        orderRequest -> onSuccess(updateOrder(Integer.parseInt(id), orderRequest), successful -> {
                                            if (successful)
                                              return complete(StatusCodes.OK);
                                            else
                                              return complete(StatusCodes.BAD_REQUEST);
                                        }))))))));
    }
}
