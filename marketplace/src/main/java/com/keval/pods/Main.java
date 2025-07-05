package com.keval.pods;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.cluster.typed.Cluster;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.server.Route;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import com.keval.pods.order.DeleteOrderActor;
import com.keval.pods.order.OrderActor;
import com.keval.pods.order.PostOrderActor;
import com.keval.pods.product.Product;
import com.keval.pods.product.ProductActor;
import com.keval.pods.product.ProductLoader;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class Main {

    static void startHttpServer(Route route, ActorSystem<?> system, Integer port) {
        System.out.println("Starting HTTP server...");
        Config config = system.settings().config();
        String interfaceName = config.getString("marketplace-app.http.server.interface");
    
        CompletionStage<ServerBinding> futureBinding =
                Http.get(system).newServerAt(interfaceName, port).bind(route);
    
        futureBinding.whenComplete((binding, exception) -> {
            if (binding != null) {
                InetSocketAddress address = binding.localAddress();
                system.log().info("\n\n\nServer online at http://{}:{}/ \n\n\n",
                        address.getHostString(),
                        address.getPort());
            } else {
                system.log().error("Failed to bind HTTP endpoint, terminating system", exception);
                system.terminate();
            }
        });
    }

    public static void main(String[] args) throws Exception {
        // Port for Akka system from command line arguments
        Integer port = Integer.valueOf(args[0]);

        // Override configuration to use port supplied as argument
        Map<String, Object> overrides = new HashMap<>();
        overrides.put("akka.remote.artery.canonical.port", port);

        // Port for HTTP server from config file
        Config conf = ConfigFactory.parseMap(overrides).withFallback(ConfigFactory.load());

        // Service keys for Group Routers of PostOrderActors and DeleteOrderActors
        ServiceKey<PostOrderActor.Command> postOrderActorSK = ServiceKey.create(PostOrderActor.Command.class, "PostOrderActorSK");
        ServiceKey<DeleteOrderActor.Command> deleteOrderActorSK = ServiceKey.create(DeleteOrderActor.Command.class, "DeleteOrderActorSK");

        // Number of PostOrderActors and DeleteOrderActors to spawn per node
        Integer NUM_POST_ORDER_ACTORS = 50;
        Integer NUM_DELETE_ORDER_ACTORS = 50;

        // Root Actor Behavior
        Behavior<NotUsed> rootBehavior = Behaviors.setup(context -> {
            // Sharding Objects
            Cluster.get(context.getSystem());
            ClusterSharding sharding = ClusterSharding.get(context.getSystem());
            
            // Initialize Product loading code
            ProductLoader productLoader = new ProductLoader();
            productLoader.loadProductsFromCsv();
            List<Integer> productIds = productLoader.productIds;
            
            // Sharding initialization for ProductActors
            sharding.init(Entity.of(ProductActor.ENTITY_TYPE_KEY, entityContext -> {
                // Obtains the entityId from `entityRefFor` call and extracts the productId from it
                String productId = ProductActor.extractProductId(entityContext.getEntityId());
                Product product = productLoader.products.get(Integer.parseInt(productId));
    
                // If product with productId not found, use a null product
                if (product == null) product = new Product();
                return ProductActor.create(productId);
            }));

            // Sharding initialization for OrderActors
            sharding.init(Entity.of(OrderActor.ENTITY_TYPE_KEY, entityContext -> OrderActor.create()));

            // Primary Node Initialization
            if (port == 8083) {
                // Create the Gateway actor
                ActorRef<Gateway.Command> gatewayActor = context.spawn(Gateway.create(sharding, postOrderActorSK, deleteOrderActorSK), "Gateway");

                // Spawn the first half of product actors in the primary node
                for (Integer i = 0; i < (productIds.size() / 2); i++) {
                    Integer productId = productIds.get(i);
                    EntityRef<ProductActor.Command> productActor = sharding.entityRefFor(ProductActor.ENTITY_TYPE_KEY, ProductActor.IdPrefix + productId);
                    productActor.tell(new ProductActor.InitializeProduct(productLoader.products.get(productId)));
                }

                // Initialize Routes
                Routes routes = new Routes(context.getSystem(), gatewayActor);

                // Start HTTP server
                Integer httpPort = Integer.valueOf(conf.getString("marketplace-app.http.server.port"));
                startHttpServer(routes.userRoutes(), context.getSystem(), httpPort);
            }
            // Secondary Node Initialization
            else {
                // Spawn the second half of product actors on the first secondary node
                // On other secondary nodes, this code should not spawn the products
                for (Integer i = (productIds.size() / 2); i < productIds.size(); i++) {
                    Integer productId = productIds.get(i);
                    EntityRef<ProductActor.Command> productActor = sharding.entityRefFor(ProductActor.ENTITY_TYPE_KEY, ProductActor.IdPrefix + productId);
                    productActor.tell(new ProductActor.InitializeProduct(productLoader.products.get(productId)));
                }
            }

            // Spawn and register PostOrderActors
            for (Integer i = 0; i < NUM_POST_ORDER_ACTORS; i++) {
              ActorRef<PostOrderActor.Command> postOrderActor = context.spawn(PostOrderActor.create(), "postOrderActor" + i);
              context.getSystem().receptionist().tell(Receptionist.register(postOrderActorSK, postOrderActor));
            }

            // Spawn and register DeleteOrderActors
            for (Integer i = 0; i < NUM_DELETE_ORDER_ACTORS; i++) {
              ActorRef<DeleteOrderActor.Command> deleteOrderActor = context.spawn(DeleteOrderActor.create(), "deleteOrderActor" + i);
              context.getSystem().receptionist().tell(Receptionist.register(deleteOrderActorSK, deleteOrderActor));
            }

            return Behaviors.empty();
        });

        // Initialize Actor System for the node
        ActorSystem.create(rootBehavior, "Marketplace", conf);
    }
}
