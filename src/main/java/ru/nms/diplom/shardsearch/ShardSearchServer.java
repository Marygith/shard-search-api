package ru.nms.diplom.shardsearch;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import ru.nms.diplom.shardsearch.module.ShardSearchServiceModule;
import ru.nms.diplom.shardsearch.service.ShardSearchServiceImpl;

public class ShardSearchServer {
    public static void main(String[] args) throws Exception {
        // Initialize Guice
        Injector injector = Guice.createInjector(new ShardSearchServiceModule());

        // Get your service implementation
        ShardSearchServiceImpl shardSearchService = injector.getInstance(ShardSearchServiceImpl.class);

        // Start gRPC Server
        Server server = ServerBuilder.forPort(9090)
                .addService(shardSearchService)
                .build()
                .start();

        System.out.println("Shard Search Service started on port 9090");

        server.awaitTermination(); // Block until server is terminated
    }
}
