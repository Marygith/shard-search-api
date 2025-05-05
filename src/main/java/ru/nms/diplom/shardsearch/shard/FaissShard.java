package ru.nms.diplom.shardsearch.shard;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import ru.nms.diplom.faiss.*;
import ru.nms.diplom.shardsearch.Document;
import ru.nms.diplom.shardsearch.service.scoreenricher.PassageReader;
import ru.nms.diplom.shardsearch.service.engine.SearchEngine;
import ru.nms.diplom.shardsearch.utils.L2DistanceComputer;

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
    private final ExecutorService executor;

    public FaissShard(FaissSearchServiceGrpc.FaissSearchServiceBlockingStub stub, PassageReader passageReader, int shardId, ExecutorService executor) {
        this.stub = stub;
        this.passageReader = passageReader;
        this.shardId = shardId;
        this.executor = executor;
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

        var requestBuilder = EncodeRequest.newBuilder().setQuery(query);
        var queryVector = stub.encodeQuery(requestBuilder.build()).getVector().getValuesList();
        List<CompletableFuture<Document>> futures = docs.stream()
                .map(docBuilder -> CompletableFuture.supplyAsync(() -> {
                    float[] docVector = passageReader.getVectorById(docBuilder.getId());
                    float distance = L2DistanceComputer.l2Distance(listToArray(queryVector), docVector);
                    return docBuilder.setFaissScore(distance).build();
                }, executor))
                .toList();

        var result = futures.stream()
                .map(CompletableFuture::join)
                .toList();

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

    private static float[] listToArray(List<Float> vector) {
        int i = 0;
        float[] array = new float[384];
        for (float value : vector) {
            array[i++] = value;
        }
        return array;
    }
}
