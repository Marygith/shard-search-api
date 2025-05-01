package ru.nms.diplom.shardsearch.service.searchengine;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import ru.nms.diplom.clusterstate.service.ShardAddressRequest;
import ru.nms.diplom.clusterstate.service.ShardServiceGrpc;
import ru.nms.diplom.shardsearch.model.IndexType;

@Singleton
public class ProxyShardBuilder {

    private final ShardServiceGrpc.ShardServiceBlockingStub stub;
    private final StubManager stubManager;

    @Inject
    public ProxyShardBuilder(StubManager stubManager) {
        this.stubManager = stubManager;
        this.stub = stubManager.getClusterStateStub();
    }

    public FaissProxyShard buildFaissProxyShard(int shardId) {
        var response = stub.getShardAddress(ShardAddressRequest.newBuilder().setShardId(shardId).setIndexType(IndexType.VECTOR.getNumber()).build());
        var shardSearchStub = stubManager.getBaseStub(response.getHost(), response.getPort());
        return new FaissProxyShard(shardSearchStub, shardId);
    }

    public LuceneProxyShard buildLuceneProxyShard(int shardId) {
        var response = stub.getShardAddress(ShardAddressRequest.newBuilder().setShardId(shardId).setIndexType(IndexType.LUCENE.getNumber()).build());
        var shardSearchStub = stubManager.getBaseStub(response.getHost(), response.getPort());
        return new LuceneProxyShard(shardSearchStub, shardId);
    }
}
