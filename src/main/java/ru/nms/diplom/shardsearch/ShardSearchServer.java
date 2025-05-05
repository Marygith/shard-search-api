package ru.nms.diplom.shardsearch;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import ru.nms.diplom.shardsearch.module.ShardSearchServiceModule;
import ru.nms.diplom.shardsearch.service.ShardSearchServiceImpl;

public class ShardSearchServer {
    public static void main(String[] args) throws Exception {
        // Initialize Guice
        long maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        long totalMemory = Runtime.getRuntime().totalMemory() / (1024 * 1024);
        System.out.println("Xms (totalMemory): " + totalMemory + " MB");
        System.out.println("Xmx (maxMemory): " + maxMemory + " MB");
        Injector injector = Guice.createInjector(new ShardSearchServiceModule());

        // Get your service implementation
        ShardSearchServiceImpl shardSearchService = injector.getInstance(ShardSearchServiceImpl.class);

        var port = Integer.parseInt(System.getenv("PORT"));
        // Start gRPC Server
        Server server = ServerBuilder.forPort(port)
                .addService(shardSearchService)
                .addService(ProtoReflectionService.newInstance())
                .build()
                .start();

        System.out.println("Shard Search Service started on port " + port);

        server.awaitTermination(); // Block until server is terminated
    }
}
