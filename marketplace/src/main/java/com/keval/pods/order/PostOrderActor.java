package com.keval.pods.order;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.keval.pods.CborSerializable;
import com.keval.pods.Routes;
import com.keval.pods.client.UsersClient;
import com.keval.pods.client.WalletsClient;
import com.keval.pods.client.model.User;
import com.keval.pods.client.model.WalletTrxn;
import com.keval.pods.product.Product;
import com.keval.pods.product.ProductActor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityRef;

public class PostOrderActor extends AbstractBehavior<PostOrderActor.Command> {
    private final static Logger log = LoggerFactory.getLogger(Routes.class);

    public interface Command extends CborSerializable {}
    public static final record PostOrderRequest(Order order, ActorRef<Order> replyTo) implements Command {}
    public static final record GetProductInfoResponse(Integer order_id, Product product) implements Command {}
    public static final record DecreaseProductStockResponse(Integer order_id, Integer product_id, Boolean successful) implements Command {}
    public static final record OrderSuccessful(Integer order_id) implements Command {}

    // ClusterSharding instance
    private final ClusterSharding sharding;

    // Actors to replyTo with final order after placement mapped by order_id
    private Map<Integer, ActorRef<Order>> replyMap = new HashMap<>();
    // Order objects for this PostOrderActor mapped by order_id
    private Map<Integer, Order> orders = new HashMap<>();
    // User objects corresponding to the orders mapped by order_id
    private Map<Integer, User> users = new HashMap<>();

    // Utility client for communicating with the Users service
    private UsersClient usersClient = new UsersClient();
    // Utility client for communicating with the Wallets service
    private WalletsClient walletsClient = new WalletsClient();

    // OrderId mapped maps for all order_items in the order request mapped by product_id. Used to merge duplicate order items.
    private Map<Integer, Map<Integer, Order.OrderItem>> ordersRequestedOrderItems = new HashMap<>();
    // OrderId mapped maps for all product details
    private Map<Integer, Map<Integer, Product>> ordersProductInfos = new HashMap<>();
    // OrderId mapped maps for all DecreaseProductStock success statuses
    private Map<Integer, Map<Integer, Boolean>> ordersDecreaseProductStockResponses = new HashMap<>();

    // Actor initialized with replyTo
    public static Behavior<Command> create() {
        return Behaviors.setup(context -> new PostOrderActor(context));
    }

    private PostOrderActor(ActorContext<Command> context) {
        super(context);
        this.sharding = ClusterSharding.get(context.getSystem());
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(PostOrderRequest.class, this::onPostOrderRequest)
                .onMessage(GetProductInfoResponse.class, this::onGetProductInfoResponse)
                .onMessage(DecreaseProductStockResponse.class, this::onDecreaseProductStockResponse)
                .onMessage(OrderSuccessful.class, this::onOrderSuccessful)
                .build();
    }

	  // Helper method to clear state related to any order from the worker
    private void clearOrderState(Integer order_id) {
        log.info("{} clearing state for order {}", getContext().getSelf().path().name(), order_id);  
        this.orders.remove(order_id);
        this.replyMap.remove(order_id);
        this.ordersRequestedOrderItems.remove(order_id);
        this.ordersProductInfos.remove(order_id);
        this.ordersDecreaseProductStockResponses.remove(order_id);
    }

    private Behavior<Command> onPostOrderRequest(PostOrderRequest command) {
        log.info("{} received order {}", getContext().getSelf().path().name(), command.order);

		    // Add order and replyTo to this worker's mapping
        this.orders.put(command.order.order_id, command.order);
		    this.replyMap.put(command.order.order_id, command.replyTo);

        Order order = this.orders.get(command.order.order_id);
        ActorRef<Order> replyTo = this.replyMap.get(order.order_id);

        // Initialize maps for received orderId
        this.ordersRequestedOrderItems.put(order.order_id, new HashMap<>());
        this.ordersProductInfos.put(order.order_id, new HashMap<>());
        this.ordersDecreaseProductStockResponses.put(order.order_id, new HashMap<>());

        Map<Integer, Order.OrderItem> requestedOrderItems = this.ordersRequestedOrderItems.get(order.order_id);

        // Check if the order has items, fail otherwise
        if (order.items.size() == 0)  {
          log.info("{} failed on order {}: Empty items list", getContext().getSelf().path().name(), order.order_id);
          replyTo.tell(new Order());
		      clearOrderState(order.order_id);
          return Behaviors.same();
        }

        // Go through all order_items and store them in requestedOrderItems while merging duplicates
        for (Order.OrderItem orderItem : order.items) {
            // If quantity is non-positive, fail
            if (orderItem.quantity <= 0) {
                log.info("{} failed on order {}: Non-positive item quantity", getContext().getSelf().path().name(), order.order_id);
                replyTo.tell(new Order());
				        clearOrderState(order.order_id);
                return Behaviors.same();
            }

            // Merge duplicate or add new orderItem to the map
            if (requestedOrderItems.containsKey(orderItem.product_id)) {
                Order.OrderItem storedOrderItem = requestedOrderItems.get(orderItem.product_id);
                storedOrderItem.quantity += orderItem.quantity;
            }
            else {
                requestedOrderItems.put(orderItem.product_id, orderItem);
            }
        }

        // Send GetProductInfo for all order_items
        for (Integer product_id : requestedOrderItems.keySet()) {
            EntityRef<ProductActor.Command> productActor = sharding.entityRefFor(ProductActor.ENTITY_TYPE_KEY, ProductActor.IdPrefix + product_id);
            productActor.tell(new ProductActor.GetProductInfoPostOrder(order.order_id, getContext().getSelf()));
        }

        return Behaviors.same();
    }

    private Behavior<Command> onGetProductInfoResponse(GetProductInfoResponse command) {
      Product product = command.product;

      Order order = this.orders.get(command.order_id);
      ActorRef<Order> replyTo = this.replyMap.get(command.order_id);

      Map<Integer, Order.OrderItem> requestedOrderItems = this.ordersRequestedOrderItems.get(command.order_id);
      Map<Integer, Product> productInfos = this.ordersProductInfos.get(command.order_id);

      // If null product, invalid product_id, fail response
      if (product.id == null) {
        log.info("{} failed on order {}: invalid order_item {}", getContext().getSelf().path().name(), command.order_id, product.id);
        replyTo.tell(new Order());
		    clearOrderState(command.order_id);
        return Behaviors.same();
      }

      // Add product info to product info map
      productInfos.put(product.id, product);

      // If all product infos have not been received, wait for more GetProductInfoResponses
      if (!productInfos.keySet().equals(requestedOrderItems.keySet()))
        return Behaviors.same();

      // ==== After all product infos have been received ====
      
      // Check for sufficient stock and compute total price
      order.total_price = 0;
      for (Integer product_id : productInfos.keySet()) {
        Product productInfo = productInfos.get(product_id);
        Order.OrderItem orderItem = requestedOrderItems.get(product_id);

        if (productInfo.stock_quantity < orderItem.quantity) {
          log.info("{} failed on order {}: insufficient stock while checking", getContext().getSelf().path().name(), order.order_id);
          replyTo.tell(new Order());
		      clearOrderState(command.order_id);
          return Behaviors.same();
        }

        order.total_price += orderItem.quantity * productInfo.price;
      }

      // Check if user exists
      Optional<User> userOptional = usersClient.getUserById(order.user_id);
      if (userOptional.isEmpty()) {
        log.info("{} failed on order {}: invalid user {}", getContext().getSelf().path().name(), order.order_id, order.user_id);
        replyTo.tell(new Order());
		    clearOrderState(command.order_id);
        return Behaviors.same();
      }

      // Successfully obtained user info
      this.users.put(command.order_id, userOptional.get());
      User user = this.users.get(command.order_id);

      // Apply discount if available
      if (!user.discount_availed) {
        order.total_price -= (order.total_price / 10);
      }

      // Debit user
      Integer debitStatus = walletsClient.putWalletTrxn(order.user_id, new WalletTrxn(WalletTrxn.DEBIT, order.total_price));
      
      // If debit fails, fail order
      if (debitStatus != 200) {
        log.info("{} failed on order {}: failed to debit wallet {} for amount {}", getContext().getSelf().path().name(), order.order_id, order.user_id, order.total_price);
        replyTo.tell(new Order());
		    clearOrderState(command.order_id);
        return Behaviors.same();
      }

      // Send DecreaseProductStock messages to all products in the order
      for (Integer product_id : requestedOrderItems.keySet()) {
        EntityRef<ProductActor.Command> productActor = sharding.entityRefFor(ProductActor.ENTITY_TYPE_KEY, ProductActor.IdPrefix + product_id);
        productActor.tell(new ProductActor.DecreaseProductStock(command.order_id, getContext().getSelf(), requestedOrderItems.get(product_id).quantity));
      }

      return Behaviors.same();
    }

    private Behavior<Command> onDecreaseProductStockResponse(DecreaseProductStockResponse command) {
      Order order = this.orders.get(command.order_id);
      ActorRef<Order> replyTo = this.replyMap.get(command.order_id);

      Map<Integer, Order.OrderItem> requestedOrderItems = this.ordersRequestedOrderItems.get(command.order_id);
      Map<Integer, Boolean> decreaseProductStockResponses = this.ordersDecreaseProductStockResponses.get(command.order_id);

      // Add response to the response map
      decreaseProductStockResponses.put(command.product_id, command.successful);

      // If responses from all products have not been received, keep waiting
      if (!decreaseProductStockResponses.keySet().equals(requestedOrderItems.keySet()))
        return Behaviors.same();
      
      // ==== After responses from all products have been received ====

      // If all stock decreases were successful, proceed to OrderSuccessful stage
      if (!decreaseProductStockResponses.containsValue(false)) {
        getContext().getSelf().tell(new OrderSuccessful(command.order_id));
        return Behaviors.same();
      }

      // ==== If atleast one of the stock decreases were unsuccessful ====

      // Restock successfully decreased products
      for (Integer product_id : decreaseProductStockResponses.keySet()) {
        if (decreaseProductStockResponses.get(product_id) == true) {
          EntityRef<ProductActor.Command> productActor = sharding.entityRefFor(ProductActor.ENTITY_TYPE_KEY, ProductActor.IdPrefix + product_id);
          productActor.tell(new ProductActor.IncreaseProductStock(requestedOrderItems.get(product_id).quantity));
        }
      }

      // Refund user
      Integer refundStatus = walletsClient.putWalletTrxn(order.user_id, new WalletTrxn(WalletTrxn.CREDIT, order.total_price));

      if (refundStatus != 200) {
        log.info("{} warning on order {}: failed to credit wallet {} with status {}", getContext().getSelf().path().name(), order.order_id, order.user_id, refundStatus);
      }
      
      // Control only reaches here if the order has failed
      replyTo.tell(new Order());
      clearOrderState(command.order_id);
      return Behaviors.same();
    }
    
    private Behavior<Command> onOrderSuccessful(OrderSuccessful command) {
      Order order = this.orders.get(command.order_id);
      User user = this.users.get(command.order_id);
      ActorRef<Order> replyTo = this.replyMap.get(command.order_id);

      // If discount was availed, send request to Users service to update discount_availed field
      if (!user.discount_availed) {
        Integer setDiscountAvailedStatus = usersClient.setUserDiscountAvailed(order.user_id, true);
        if (setDiscountAvailedStatus != 202)
          log.info("{} warning on order {}: failed to set discount_availed for user {} with status {}", getContext().getSelf().path().name(), order.order_id, order.user_id, setDiscountAvailedStatus);
      } 
      
      // Create a new sharded OrderActor for the successfully placed order
      order.status = Order.STATUS_PLACED;

      EntityRef<OrderActor.Command> orderEntityRef = sharding.entityRefFor(OrderActor.ENTITY_TYPE_KEY, OrderActor.IdPrefix + order.order_id);
      orderEntityRef.tell(new OrderActor.InitializeOrder(order));

      // Send order object to routes for sending response
      replyTo.tell(order);

      // Clear order state from this worker
      clearOrderState(command.order_id);
      return Behaviors.same();
    }
}
