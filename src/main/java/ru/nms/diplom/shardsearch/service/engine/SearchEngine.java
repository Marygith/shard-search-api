package ru.nms.diplom.shardsearch.service.engine;

import ru.nms.diplom.shardsearch.Document;

import java.util.List;

public interface SearchEngine {
    List<Document.Builder> searchDocs(String query, int k, List<Float> encodedQuery);
    List<Document> enrichWithSimilarityScores(List<Document.Builder> docs, String query, List<Float> encodedQuery);
}