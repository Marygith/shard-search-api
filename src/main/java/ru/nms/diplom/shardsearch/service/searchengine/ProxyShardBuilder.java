package ru.nms.diplom.shardsearch.service.searchengine;

import com.google.inject.Singleton;
import io.grpc.ManagedChannelBuilder;
import ru.nms.diplom.clusterstate.service.ShardIpRequest;
import ru.nms.diplom.clusterstate.service.ShardServiceGrpc;
import ru.nms.diplom.shardsearch.model.IndexType;

@Singleton
public class ProxyShardBuilder {

    private final ShardServiceGrpc.ShardServiceBlockingStub stub;
    private final StubManager stubManager;

    public ProxyShardBuilder(ShardServiceGrpc.ShardServiceBlockingStub stub, StubManager stubManager) {
        this.stub = stub;
        this.stubManager = stubManager;
    }

    public FaissProxyShard buildFaissProxyShard(int shardId) {
        var ip = stub.getShardIp(ShardIpRequest.newBuilder().setShardId(shardId).setIndexType(IndexType.VECTOR.getNumber()).build()).getIp();
        var shardSearchStub = stubManager.getBaseStub(ip);
        return new FaissProxyShard(shardSearchStub, shardId);
    }

    public LuceneProxyShard buildLuceneProxyShard(int shardId) {
        var ip = stub.getShardIp(ShardIpRequest.newBuilder().setShardId(shardId).setIndexType(IndexType.LUCENE.getNumber()).build()).getIp();
        var shardSearchStub = stubManager.getBaseStub(ip);
        return new LuceneProxyShard(shardSearchStub, shardId);
    }
}
