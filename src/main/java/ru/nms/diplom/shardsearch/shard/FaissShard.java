package ru.nms.diplom.shardsearch.shard;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import ru.nms.diplom.faiss.*;
import ru.nms.diplom.shardsearch.Document;
import ru.nms.diplom.shardsearch.service.scoreenricher.PassageReader;
import ru.nms.diplom.shardsearch.service.engine.SearchEngine;

import java.util.ArrayList;
import java.util.List;

public class FaissShard implements SearchEngine {
    private final FaissSearchServiceGrpc.FaissSearchServiceBlockingStub stub;
    private final PassageReader passageReader;
    private final int shardId;
    private final AtomicLong overallSearchDocTime = new AtomicLong(0);
    private final AtomicLong overallSimilarityScoresTime = new AtomicLong(0);
    private final AtomicInteger overallSearchDocCounter = new AtomicInteger(0);
    private final AtomicInteger overallSimilarityScoresCounter = new AtomicInteger(0);

    public FaissShard(FaissSearchServiceGrpc.FaissSearchServiceBlockingStub stub, PassageReader passageReader, int shardId) {
        this.stub = stub;
        this.passageReader = passageReader;
        this.shardId = shardId;
    }

    @Override
    public List<Document.Builder> searchDocs(String query, int k) {
//        System.out.println("Searching docs in faiss shard " + shardId);

        var start = System.currentTimeMillis();
        FaissSearchRequest request = FaissSearchRequest.newBuilder().setQuery(query).setK(k).build();
        FaissSearchResponse response = stub.search(request);
        List<Document.Builder> results = new ArrayList<>();
        for (var faissResult : response.getResultsList()) {
            results.add(Document.newBuilder().setId(faissResult.getId()).setFaissScore(faissResult.getScore()).setLuceneScore(0f));
        }
//        System.out.printf("Got docs from faiss shard %s with ids: %s%n", shardId, results.stream().map(Document.Builder::getId).toList());
//        System.out.printf("Got %s docs from faiss shard %s%n", response.getResultsList().size(), shardId);
        overallSearchDocTime.addAndGet(System.currentTimeMillis() - start);
        overallSearchDocCounter.incrementAndGet();
        return results;
    }

    @Override
    public List<Document> enrichWithSimilarityScores(List<Document.Builder> docs, String query) {
        var start = System.currentTimeMillis();

        if (docs.isEmpty()) {
//            System.out.println("faiss similarity stage was initiated for empty docs");

            return List.of();
        }
//        System.out.printf("Searching similarity scores in faiss shard %s, docs ids: %s%n", shardId, docs.stream().map(Document.Builder::getId).toList());
//        System.out.printf("Searching similarity scores in faiss shard %s, for %s docs%n", shardId, docs.size());

        var requestBuilder = SimilarityRequest.newBuilder().setQuery(query);
        docs.forEach(doc -> requestBuilder.putIdToVector(doc.getId(), Vector.newBuilder().addAllValues(passageReader.getVectorById(doc.getId())).build()));
        var scoresMap = stub.getSimilarityScores(requestBuilder.build()).getScoresMap();
//        System.out.printf("Got %s similarity scores from faiss shard %s%n", scoresMap.size(), shardId);

        var result =  docs.stream().map(d -> d.setFaissScore(scoresMap.get(d.getId())).build()).toList();
        overallSimilarityScoresTime.addAndGet(System.currentTimeMillis() - start);
        overallSimilarityScoresCounter.incrementAndGet();
        return result;
    }

    public int getShardId() {
        return shardId;
    }

    public AtomicLong getOverallSearchDocTime() {
        return overallSearchDocTime;
    }

    public AtomicLong getOverallSimilarityScoresTime() {
        return overallSimilarityScoresTime;
    }

    public AtomicInteger getOverallSearchDocCounter() {
        return overallSearchDocCounter;
    }

    public AtomicInteger getOverallSimilarityScoresCounter() {
        return overallSimilarityScoresCounter;
    }
}
