package ru.nms.diplom.shardsearch.service.searchengine;

import ru.nms.diplom.faiss.*;
import ru.nms.diplom.shardsearch.Document;
import ru.nms.diplom.shardsearch.service.scoreenricher.PassageReader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class FaissShard implements SearchEngine {
    private final FaissSearchServiceGrpc.FaissSearchServiceBlockingStub stub;
    private final PassageReader passageReader;
    private final int shardId;

    public FaissShard(FaissSearchServiceGrpc.FaissSearchServiceBlockingStub stub, PassageReader passageReader, int shardId) {
        this.stub = stub;
        this.passageReader = passageReader;
        this.shardId = shardId;
    }

    @Override
    public List<Document.Builder> searchDocs(String query, int k) {
        System.out.println("Searching docs in faiss shard " + shardId);

        FaissSearchRequest request = FaissSearchRequest.newBuilder().setQuery(query).setK(k).build();
        FaissSearchResponse response = stub.search(request);
        List<Document.Builder> results = new ArrayList<>();
        for (var faissResult : response.getResultsList()) {
            results.add(Document.newBuilder().setId(faissResult.getId()).setFaissScore(faissResult.getScore()).setLuceneScore(0f));
        }
        System.out.printf("Got docs from faiss shard %s with ids: %s%n", shardId, results.stream().map(Document.Builder::getId).toList());
        return results;
    }

    @Override
    public List<Document> enrichWithSimilarityScores(List<Document.Builder> docs, String query) {
        if (docs.isEmpty()) {
            return List.of();
        }
        System.out.printf("Searching similarity scores in faiss shard %s, docs ids: %s%n", shardId, docs.stream().map(Document.Builder::getId).toList());

        var requestBuilder = SimilarityRequest.newBuilder().setQuery(query);
        docs.forEach(doc -> requestBuilder.putIdToVector(doc.getId(), Vector.newBuilder().addAllValues(passageReader.getVectorById(doc.getId())).build()));
        var scoresMap = stub.getSimilarityScores(requestBuilder.build()).getScoresMap();
        System.out.printf("Got dimilarity scores from faiss shard %s with ids: %s%n", shardId, scoresMap.keySet());

        return docs.stream().map(d -> d.setFaissScore(scoresMap.get(d.getId())).build()).toList();
    }
}
