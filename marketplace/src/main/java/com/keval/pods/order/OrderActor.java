package com.keval.pods.order;

import com.keval.pods.CborSerializable;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;

public class OrderActor extends AbstractBehavior<OrderActor.Command> {

    public interface Command extends CborSerializable {}
    public static final record InitializeOrder(Order order) implements Command {}
    public static final record GetOrder(ActorRef<Order> replyTo) implements Command {}
    public static final record UpdateOrderStatus(ActorRef<Boolean> replyTo, Order orderUpdate) implements Command {}
    public static final record CancelOrder(ActorRef<DeleteOrderActor.Command> replyTo) implements Command {}
    public static final record GetOrderDeleteOrder(ActorRef<DeleteOrderActor.Command> replyTo) implements Command {}

    public static final EntityTypeKey<OrderActor.Command> ENTITY_TYPE_KEY =
            EntityTypeKey.create(OrderActor.Command.class, "OrderActor");

    // All orderActors will have a name of the form "orderActor{orderId}"
    public static final String IdPrefix = "OrderActor";
    
    // Extracts the product ID from an actor name string
    public static String extractProductId(String name) {
        return name.substring(OrderActor.IdPrefix.length());
    }

    // Initialize with null Order
    private Order order = new Order();

    public static Behavior<Command> create() {
        return Behaviors.setup(OrderActor::new);
    }

    private OrderActor(ActorContext<Command> context) {
        super(context);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(InitializeOrder.class, this::onInitializeOrder)
                .onMessage(GetOrder.class, this::onGetOrder)
                .onMessage(UpdateOrderStatus.class, this::onUpdateOrderStatus)
                .onMessage(CancelOrder.class, this::onCancelOrder)
                .onMessage(GetOrderDeleteOrder.class, this::onGetOrderDeleteOrder)
                .build();
    }

    private Behavior<Command> onInitializeOrder(InitializeOrder command) {
        System.out.println(getContext().getSelf().path().name() + " created");
        this.order = command.order;
        return Behaviors.same();
    }

    private Behavior<Command> onGetOrder(GetOrder command) {
        System.out.println(getContext().getSelf().path().name() + " received get order command");
        command.replyTo.tell(this.order);
        return Behaviors.same();
    }

    private Behavior<Command> onUpdateOrderStatus(UpdateOrderStatus command) {
        System.out.println(getContext().getSelf().path().name() + " received update order state command to status " + command.orderUpdate.status);
        
        // If this a null OrderActor, fail request
        if (this.order.order_id == null) {
            command.replyTo.tell(false);
            return Behaviors.same();
        }

        // Only allow change to delivered status
        if (!command.orderUpdate.status.equals(Order.STATUS_DELIVERED))
            command.replyTo.tell(false);
        
        // Only allow if current status is placed
        else if (!this.order.status.equals(Order.STATUS_PLACED))
            command.replyTo.tell(false);
        
        else {
            this.order.status = Order.STATUS_DELIVERED;
            System.out.println(getContext().getSelf().path().name() + " set status to " + Order.STATUS_DELIVERED);
            command.replyTo.tell(true);
        }

        return Behaviors.same();
    }

    private Behavior<Command> onCancelOrder(CancelOrder command) {
        // If this is a valid order and current status is placed, cancel order and give success response
        if (this.order.order_id != null && this.order.status.equals(Order.STATUS_PLACED)) {
            this.order.status = Order.STATUS_CANCELLED;
            command.replyTo.tell(new DeleteOrderActor.CancelOrderResponse(this.order.order_id, true));
        }
        else
        {
            command.replyTo.tell(new DeleteOrderActor.CancelOrderResponse(this.order.order_id, false));
        }

        return Behaviors.same();
    }

    private Behavior<Command> onGetOrderDeleteOrder(GetOrderDeleteOrder command) {
        System.out.println(getContext().getSelf().path().name() + " received get order command");
        command.replyTo.tell(new DeleteOrderActor.GetOrderResponse(this.order));
        return Behaviors.same();
    }
}
