package ru.nms.diplom.shardsearch.factory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import ru.nms.diplom.clusterstate.service.ShardAddressRequest;
import ru.nms.diplom.clusterstate.service.ShardServiceGrpc;
import ru.nms.diplom.shardsearch.model.IndexType;
import ru.nms.diplom.shardsearch.service.engine.StubManager;
import ru.nms.diplom.shardsearch.shard.FaissProxyShard;
import ru.nms.diplom.shardsearch.shard.LuceneProxyShard;

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
        String nodeId = System.getenv("NODE_ID");
        System.out.printf("node %s missing shard %s of type FAISS, asking for address%n", nodeId, shardId);
        var response = stub.getShardAddress(ShardAddressRequest.newBuilder().setShardId(shardId).setIndexType(IndexType.VECTOR.getNumber()).build());
        System.out.printf("node %s about to build proxy faiss shard with host %s and port %s for shard %s%n", nodeId, response.getHost(), response.getPort(), shardId);
        var shardSearchStub = stubManager.getBaseStub(response.getHost(), response.getPort());
        return new FaissProxyShard(shardSearchStub, shardId);
    }

    public LuceneProxyShard buildLuceneProxyShard(int shardId) {
        String nodeId = System.getenv("NODE_ID");
        System.out.printf("node %s missing shard %s of type LUCENE, asking for address%n", nodeId, shardId);
        var response = stub.getShardAddress(ShardAddressRequest.newBuilder().setShardId(shardId).setIndexType(IndexType.LUCENE.getNumber()).build());
        System.out.printf("node %s about to build proxy lucene shard with host %s and port %s for shard %s%n", nodeId, response.getHost(), response.getPort(), shardId);

        var shardSearchStub = stubManager.getBaseStub(response.getHost(), response.getPort());
        return new LuceneProxyShard(shardSearchStub, shardId);
    }
}
