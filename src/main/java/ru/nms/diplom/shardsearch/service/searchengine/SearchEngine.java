package ru.nms.diplom.shardsearch.service.searchengine;

import ru.nms.diplom.shardsearch.Document;
import ru.nms.diplom.shardsearch.ShardSearchRequest;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface SearchEngine {
    List<Document.Builder> searchDocs(String query, int k);
    List<Document> enrichWithSimilarityScores(List<Document.Builder> docs, String query);
}