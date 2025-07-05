package com.keval.pods.order;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.keval.pods.CborSerializable;
import com.keval.pods.Routes;
import com.keval.pods.client.WalletsClient;
import com.keval.pods.client.model.WalletTrxn;
import com.keval.pods.product.ProductActor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityRef;

public class DeleteOrderActor extends AbstractBehavior<DeleteOrderActor.Command> {
    private final static Logger log = LoggerFactory.getLogger(Routes.class);

    public interface Command extends CborSerializable {}
    public static final record DeleteOrderRequest(Integer orderId, ActorRef<Boolean> replyTo) implements Command {}
    public static final record CancelOrderResponse(Integer orderId, Boolean successful) implements Command {}
    public static final record GetOrderResponse(Order order) implements Command {}

    // ClusterSharding instance
    private final ClusterSharding sharding;

    // Actors to reply to after order cancellation mapped by order_id
    private final Map<Integer, ActorRef<Boolean>> replyMap = new HashMap<>();

    // Utility client for communicating with the Wallets service
    private WalletsClient walletsClient = new WalletsClient();

    public static Behavior<Command> create() {
        return Behaviors.setup(context -> new DeleteOrderActor(context));
    }

    private DeleteOrderActor(ActorContext<Command> context) {
        super(context);
        this.sharding = ClusterSharding.get(getContext().getSystem());
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(DeleteOrderRequest.class, this::onDeleteOrderRequest)
                .onMessage(CancelOrderResponse.class, this::onCancelOrderResponse)
                .onMessage(GetOrderResponse.class, this::onGetOrderResponse)
                .build();
    }

    // Helper method to clear state related to any order from the worker
    private void clearOrderState(Integer order_id) {
        this.replyMap.remove(order_id);
        // this.ordersProductQuantitiesToIncrease.remove(order_id);
    }

    private Behavior<Command> onDeleteOrderRequest(DeleteOrderRequest message) {
        log.info("{} received orderId {}", getContext().getSelf().path().name(), message.orderId);

        // If there is already a pending cancel request for the order_id, fail
        // NOTE: This does not prevent multiple cancel requests for the same order_id received by
        // different CancelOrder actors but it still simplifies the internal logic of one DeleteOrderActor
        if (this.replyMap.containsKey(message.orderId)) {
            log.info("{} info on orderId {}: Failed due to pending cancel request", getContext().getSelf().path().name(), message.orderId);
            message.replyTo.tell(false);
            return Behaviors.same();
        }

        // Otherwise, intialize state
        this.replyMap.put(message.orderId, message.replyTo);

        // Send cancellation message to OrderActor
        EntityRef<OrderActor.Command> orderActor = sharding.entityRefFor(OrderActor.ENTITY_TYPE_KEY, OrderActor.IdPrefix + message.orderId);
        orderActor.tell(new OrderActor.CancelOrder(getContext().getSelf()));

        return Behaviors.same();
    } 

    private Behavior<Command> onCancelOrderResponse(CancelOrderResponse message) {
        ActorRef<Boolean> replyTo = this.replyMap.get(message.orderId);

        // If order cancellation failed, send fail resposne
        if (!message.successful) {
            log.info("{} info on order {}: CancelOrder failed", getContext().getSelf().path().name(), message.orderId); 
            replyTo.tell(false);
            clearOrderState(message.orderId);
            return Behaviors.same();
        }
        
        // Otherwise, obtain userId of from the order actor
        EntityRef<OrderActor.Command> orderActor = sharding.entityRefFor(OrderActor.ENTITY_TYPE_KEY, OrderActor.IdPrefix + message.orderId);
        orderActor.tell(new OrderActor.GetOrderDeleteOrder(getContext().getSelf()));

        return Behaviors.same();
    }

    private Behavior<Command> onGetOrderResponse(GetOrderResponse message) {
        Order order = message.order;
        log.info("{} info on order {}: cancelled order {}", getContext().getSelf().path().name(), order.order_id, order);

        // Restock all products in the order
        for (Order.OrderItem item : order.items) {
            EntityRef<ProductActor.Command> productActor = sharding.entityRefFor(ProductActor.ENTITY_TYPE_KEY, ProductActor.IdPrefix + item.product_id);
            productActor.tell(new ProductActor.IncreaseProductStock(item.quantity));
        }

        // Refund user
        Integer refundStatus = walletsClient.putWalletTrxn(order.user_id, new WalletTrxn(WalletTrxn.CREDIT, order.total_price));
        if (refundStatus != 200) {
            log.warn("{} warning on order {}: failed to credit wallet {} for amount {}", getContext().getSelf().path().name(), order.order_id, order.user_id, order.total_price);
        } else {
            log.info("{} info on order {}: credited wallet {} for amount {}", getContext().getSelf().path().name(), order.order_id, order.user_id, order.total_price);
        }

        ActorRef<Boolean> replyTo = this.replyMap.get(order.order_id);
        replyTo.tell(true);
        
        clearOrderState(order.order_id);
        return Behaviors.same();
    }
}
