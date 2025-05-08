package ru.nms.diplom.shardsearch.shard;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import ru.nms.diplom.shardsearch.Document;
import ru.nms.diplom.shardsearch.ShardSearchServiceGrpc;
import ru.nms.diplom.shardsearch.SimilarityScoresRequest;
import ru.nms.diplom.shardsearch.model.IndexType;
import ru.nms.diplom.shardsearch.service.engine.SearchEngine;

import java.util.List;

public class LuceneProxyShard implements SearchEngine {
    private final ShardSearchServiceGrpc.ShardSearchServiceBlockingStub stub;
    private final int shardId;
    private final AtomicLong overallSimilarityScoresTime = new AtomicLong(0);
    private final AtomicInteger overallSimilarityScoresCounter = new AtomicInteger(0);

    public LuceneProxyShard(ShardSearchServiceGrpc.ShardSearchServiceBlockingStub stub, int shardId) {
        this.stub = stub;
        this.shardId = shardId;
    }

    @Override
    public List<Document.Builder> searchDocs(String query, int k, List<Float> encodedQuery) {
        throw new UnsupportedOperationException("Proxy shards are supposed to be used only for similarity requests");
    }

    @Override
    public List<Document> enrichWithSimilarityScores(List<Document.Builder> docs, String query, List<Float> encodedQuery) {
        var start = System.currentTimeMillis();

        if (docs.isEmpty()) {
            System.out.println("lucene proxy similarity stage was initiated for empty docs, strange");

            return List.of();
        }
//        System.out.printf("Searching similarity scores in lucene proxy shard %s, docs ids: %s%n", shardId, docs.stream().map(Document.Builder::getId).toList());
        System.out.printf("Searching similarity scores in lucene proxy shard %s, for %s docs%n", shardId, docs.size());

        var scoresRequestBuilder = SimilarityScoresRequest.newBuilder();
        docs.forEach(d -> scoresRequestBuilder.addDocuments(d.build()));
        var result = stub.getSimilarityScores(
                scoresRequestBuilder
                    .setShardId(shardId)
                    .setQuery(query)
                    .setIndexType(IndexType.LUCENE.getNumber())
                    .build())
            .getResultsList();
        overallSimilarityScoresTime.addAndGet(System.currentTimeMillis() - start);
        overallSimilarityScoresCounter.incrementAndGet();
        return result;

    }

    public int getShardId() {
        return shardId;
    }

    public AtomicLong getOverallSimilarityScoresTime() {
        return overallSimilarityScoresTime;
    }

    public AtomicInteger getOverallSimilarityScoresCounter() {
        return overallSimilarityScoresCounter;
    }
}