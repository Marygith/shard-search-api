package ru.nms.diplom.shardsearch.service.engine;

import com.google.inject.Singleton;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import ru.nms.diplom.clusterstate.service.ShardServiceGrpc;
import ru.nms.diplom.faiss.FaissSearchServiceGrpc;
import ru.nms.diplom.shardsearch.ShardSearchServiceGrpc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class StubManager {
    private final Map<String, ShardSearchServiceGrpc.ShardSearchServiceBlockingStub> stubPool = new ConcurrentHashMap<>();
    private final Map<Integer, FaissSearchServiceGrpc.FaissSearchServiceBlockingStub> faissShardStubPool = new ConcurrentHashMap<>();
    private final String clusterStateApiHost = System.getenv().getOrDefault("CLUSTER_STATE_HOST", "localhost");

    public ShardSearchServiceGrpc.ShardSearchServiceBlockingStub getBaseStub(String host, int port) {
        return stubPool.computeIfAbsent(host + port, address -> {
            ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                    .usePlaintext()
                    .maxInboundMessageSize(10000000)
                    .build();
            return ShardSearchServiceGrpc.newBlockingStub(channel);
        });
    }

    public FaissSearchServiceGrpc.FaissSearchServiceBlockingStub getFaissStub(int shardId) {
        return faissShardStubPool.computeIfAbsent(shardId, id -> {
            int port = 50000 + id;
            String target = "localhost";
            ManagedChannel channel = ManagedChannelBuilder.forAddress(target, port)
                    .usePlaintext()
                    .maxInboundMessageSize(10000000)
                    .build();
            return FaissSearchServiceGrpc.newBlockingStub(channel);
        });
    }

    public ShardServiceGrpc.ShardServiceBlockingStub getClusterStateStub() {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(clusterStateApiHost, 8081)
                .usePlaintext()
                .build();
        return ShardServiceGrpc.newBlockingStub(channel);
    }
}