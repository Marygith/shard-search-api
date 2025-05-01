package ru.nms.diplom.shardsearch.service.searchengine;

import ru.nms.diplom.shardsearch.Document;
import ru.nms.diplom.shardsearch.ShardSearchServiceGrpc;
import ru.nms.diplom.shardsearch.SimilarityScoresRequest;
import ru.nms.diplom.shardsearch.model.IndexType;

import java.util.List;

public class LuceneProxyShard implements SearchEngine{
    private final ShardSearchServiceGrpc.ShardSearchServiceBlockingStub stub;
    private final int shardId;
    public LuceneProxyShard(ShardSearchServiceGrpc.ShardSearchServiceBlockingStub stub, int shardId) {
        this.stub = stub;
        this.shardId = shardId;
    }

    @Override
    public List<Document.Builder> searchDocs(String query, int k) {
        throw new UnsupportedOperationException("Proxy shards are supposed to be used only for similarity requests");
    }

    @Override
    public List<Document> enrichWithSimilarityScores(List<Document.Builder> docs, String query) {
        if (docs.isEmpty()) {
            return List.of();
        }
        System.out.printf("Searching similarity scores in lucene proxy shard %s, docs ids: %s%n", shardId, docs.stream().map(Document.Builder::getId).toList());

        var scoresRequestBuilder = SimilarityScoresRequest.newBuilder();
        docs.forEach(d -> scoresRequestBuilder.addDocuments(d.build()));
        return stub.getSimilarityScores(
                        scoresRequestBuilder
                                .setShardId(shardId)
                                .setIndexType(IndexType.LUCENE.getNumber())
                                .build())
                .getResultsList();
    }
}