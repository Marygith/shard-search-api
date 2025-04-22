package ru.nms.diplom.shardsearch.service.searchengine;

import com.google.inject.Singleton;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import ru.nms.diplom.shardsearch.ShardSearchServiceGrpc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class StubManager {
    private final Map<String, ShardSearchServiceGrpc.ShardSearchServiceBlockingStub> stubPool = new ConcurrentHashMap<>();

    public ShardSearchServiceGrpc.ShardSearchServiceBlockingStub getBaseStub(String ip) {
        return stubPool.computeIfAbsent(ip, address -> {
            ManagedChannel channel = ManagedChannelBuilder.forAddress(address, 9090)
                    .usePlaintext()
                    .build();
            return ShardSearchServiceGrpc.newBlockingStub(channel); // base stub
        });
    }
}