package com.keval.pods.product;

import com.keval.pods.CborSerializable;
import com.keval.pods.order.PostOrderActor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;

public class ProductActor extends AbstractBehavior<ProductActor.Command> {

    public interface Command extends CborSerializable {}

    // Initialize product actor with product
    public static final record InitializeProduct(Product product) implements Command {}
    // Get product info request for Gateway
    public static final record GetProductInfo(ActorRef<Product> replyTo) implements Command {}
    // Get product info request for PostOrderActor
    public static final record GetProductInfoPostOrder(Integer order_id, ActorRef<PostOrderActor.Command> replyTo) implements Command {}
    // Decrease product stock
    public static final record DecreaseProductStock(Integer order_id, ActorRef<PostOrderActor.Command> replyTo, Integer quantity) implements Command {}
    // Increase product stock
    public static final record IncreaseProductStock(Integer quantity) implements Command {}

    private Product product;

    public static final EntityTypeKey<Command> ENTITY_TYPE_KEY =
            EntityTypeKey.create(Command.class, "ProductActor");
    
    // All ProductActors will have a name of the form "ProductActor{productId}"
    public static final String IdPrefix = "ProductActor";
    
    // Extracts the product ID from an actor name string
    public static String extractProductId(String name) {
        return name.substring(ProductActor.IdPrefix.length());
    }

    public ProductActor(ActorContext<Command> context, String id) {
        super(context);
        // Initialize with null product
        this.product = new Product();
    }

    public static Behavior<Command> create(String id) {
        System.out.println("ProductActor Created with name: " + ProductActor.IdPrefix + id);
        return Behaviors.setup(context -> new ProductActor(context, id));
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(InitializeProduct.class, this::onInitializeProduct)
                .onMessage(GetProductInfo.class, this::onGetProductInfo)
                .onMessage(GetProductInfoPostOrder.class, this::onGetProductInfoPostOrder)
                .onMessage(DecreaseProductStock.class, this::onDecreaseProductStock)
                .onMessage(IncreaseProductStock.class, this::onIncreaseProductStock)
                .onSignal(akka.actor.typed.Signal.class, signal -> Behaviors.stopped()) // stop on signal
                .build();
    }

    private Behavior<Command> onInitializeProduct(InitializeProduct message) {
      System.out.println(getContext().getSelf().path().name() + " received initialize product command");
      if (this.product.id == null) this.product = message.product;
      return Behaviors.same();
    }

    private Behavior<Command> onGetProductInfo(GetProductInfo message) {
        System.out.println(getContext().getSelf().path().name() + " received get product command");
        message.replyTo.tell(product);
        return Behaviors.same();
    }

    private Behavior<Command> onGetProductInfoPostOrder(GetProductInfoPostOrder message) {
        System.out.println(getContext().getSelf().path().name() + " received get product command");
        message.replyTo.tell(new PostOrderActor.GetProductInfoResponse(message.order_id, product));
        return Behaviors.same();
    }

    private Behavior<Command> onDecreaseProductStock(DecreaseProductStock message) {
        System.out.println(getContext().getSelf().path().name() + " received DecreaseProductStock(" + message.quantity + ")");
        
        // If insufficient stock, reply with failure message
        if (this.product.stock_quantity < message.quantity)
            message.replyTo.tell(new PostOrderActor.DecreaseProductStockResponse(message.order_id, this.product.id, false));
        // If sufficient stock, decrease and reply with success message
        else {
            this.product.stock_quantity -= message.quantity;
            message.replyTo.tell(new PostOrderActor.DecreaseProductStockResponse(message.order_id, this.product.id, true));
        }

        return Behaviors.same();        
    }

    private Behavior<Command> onIncreaseProductStock(IncreaseProductStock message) {
        System.out.println(getContext().getSelf().path().name() + " received IncreaseProductStock(" + message.quantity + ")");
        this.product.stock_quantity += message.quantity;
        return Behaviors.same();
    }
}
