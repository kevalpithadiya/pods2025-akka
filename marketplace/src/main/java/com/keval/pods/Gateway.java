package com.keval.pods;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.GroupRouter;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.Routers;
import akka.actor.typed.receptionist.ServiceKey;

import com.keval.pods.order.DeleteOrderActor;
import com.keval.pods.order.Order;
import com.keval.pods.order.OrderActor;
import com.keval.pods.order.PostOrderActor;
import com.keval.pods.product.Product;
import com.keval.pods.product.ProductActor;

import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityRef;

// import com.keval.pods.OrderActor;


public class Gateway extends AbstractBehavior<Gateway.Command> {

    public interface Command extends CborSerializable {}

    // GET /products/{productId}
    public static final record GetProduct(Integer productId, ActorRef<Product> replyTo) implements Command {}
    // POST /orders
    // Body: Order
    public static final record CreateOrder(Order order, ActorRef<Order> replyTo) implements Command {}
    // GET /orders/{orderId}
    public static final record GetOrder(Integer orderId, ActorRef<Order> replyTo) implements Command {}
    // PUT /orders/{orderId}
    // Body: Order
    public static final record UpdateOrder(Integer orderId, Order order, ActorRef<Boolean> replyTo) implements Command {}
    // DELETE /orders/{orderId}
    public static final record CancelOrder(Integer orderId, ActorRef<Boolean> replyTo) implements Command {}

    private final ClusterSharding sharding;
    private final ActorRef<PostOrderActor.Command> postOrderActorRouter;
    private final ActorRef<DeleteOrderActor.Command> deleteOrderActorRouter;

    private Integer order_count = 0;

    public static Behavior<Command> create(ClusterSharding sharding, ServiceKey<PostOrderActor.Command> postOrderActorSK, ServiceKey<DeleteOrderActor.Command> deleteOrderActorSK) {
        return Behaviors.setup(context -> new Gateway(context, sharding, postOrderActorSK, deleteOrderActorSK));
    }

    private Gateway(ActorContext<Command> context, ClusterSharding sharding, ServiceKey<PostOrderActor.Command> postOrderActorSK, ServiceKey<DeleteOrderActor.Command> deleteOrderActorSK) {
        super(context);
        this.sharding = sharding;

        // Initialize and spawn Group Router for PostOrderActors
        GroupRouter<PostOrderActor.Command> postOrderActorGroup = Routers.group(postOrderActorSK);
        this.postOrderActorRouter = context.spawn(postOrderActorGroup, "postOrderActorGroup");

        // Initialize and spawn Group Router for DeleteOrderActors
        GroupRouter<DeleteOrderActor.Command> deleteOrderActorGroup = Routers.group(deleteOrderActorSK);
        this.deleteOrderActorRouter = context.spawn(deleteOrderActorGroup, "deleteOrderActorGroup");
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(GetProduct.class, this::onGetProduct)
                .onMessage(CreateOrder.class, this::onCreateOrder)
                .onMessage(GetOrder.class, this::onGetOrder)
                .onMessage(UpdateOrder.class, this::onUpdateOrder)
                .onMessage(CancelOrder.class, this::onCancelOrder)
                .build();
    }

    // Forwards request from GET /products/{productId} to the corresponding product actor
    private Behavior<Command> onGetProduct(GetProduct message) {
        System.out.println("Gateway received GetProduct for product ID: " + message.productId);

        // Parse productId from the message
        String productId = String.valueOf(message.productId);

        // Obtain entityRef for the corresponding product actor
        EntityRef<ProductActor.Command> productActorRef =
                sharding.entityRefFor(ProductActor.ENTITY_TYPE_KEY, ProductActor.IdPrefix + productId);

        productActorRef.tell(new ProductActor.GetProductInfo(message.replyTo));
        return Behaviors.same();
    }

    // Forwards request from POST /orders to a PostOrderActor corresponding
    private Behavior<Command> onCreateOrder(CreateOrder message) {
        // Obtain order_id from global (w.r.t Gateway) order_count
        message.order.order_id = order_count++;

        // Send PostOrderRequest without Order request to the PostOrderActor
        postOrderActorRouter.tell(new PostOrderActor.PostOrderRequest(message.order, message.replyTo));
        return Behaviors.same();
    }

    // Forwards request from GET /orders/{orderId} to the corresponding order actor
    private Behavior<Command> onGetOrder(GetOrder message) {
        EntityRef<OrderActor.Command> orderActorRef = sharding.entityRefFor(OrderActor.ENTITY_TYPE_KEY, OrderActor.IdPrefix + message.orderId);
        orderActorRef.tell(new OrderActor.GetOrder(message.replyTo));
        return Behaviors.same();
    }
    
    // Forwards request from PUT /orders/{orderId} to the corresponding order actor
    private Behavior<Command> onUpdateOrder(UpdateOrder message) {
        // Verify that the orderId in path and payload are same
        if (message.orderId == message.order.order_id) {
            EntityRef<OrderActor.Command> orderActorRef = sharding.entityRefFor(OrderActor.ENTITY_TYPE_KEY, OrderActor.IdPrefix + message.order.order_id);
            orderActorRef.tell(new OrderActor.UpdateOrderStatus(message.replyTo, message.order));
        }
        // If not, skip messaging OrderActor and directly send fail response
        else 
            message.replyTo.tell(false);

        return Behaviors.same();
    }

    private Behavior<Command> onCancelOrder(CancelOrder message) {
        deleteOrderActorRouter.tell(new DeleteOrderActor.DeleteOrderRequest(message.orderId, message.replyTo));
        return Behaviors.same();
    }
}
