package ru.nms.diplom.shardsearch.shard;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import ru.nms.diplom.shardsearch.Document;
import ru.nms.diplom.shardsearch.service.engine.SearchEngine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;

public class LuceneShard implements SearchEngine {
    private final IndexSearcher searcher;
    private final QueryParser parser;
    private final int shardId;
    private final AtomicLong overallSearchDocTime = new AtomicLong(0);
    private final AtomicLong overallSimilarityScoresTime = new AtomicLong(0);
    private final AtomicInteger overallSearchDocCounter = new AtomicInteger(0);
    private final AtomicInteger overallSimilarityScoresCounter = new AtomicInteger(0);

    public LuceneShard(Path indexPath, int shardId) throws IOException {
        this.shardId = shardId;
        IndexReader reader = DirectoryReader.open(FSDirectory.open(indexPath));
        this.searcher = new IndexSearcher(reader);
        this.parser = new QueryParser("contents", new StandardAnalyzer());
    }

    @Override
    public List<Document.Builder> searchDocs(String query, int k) {
        var start = System.currentTimeMillis();

//        System.out.println("Searching docs in lucene shard " + shardId);

        try {
            Query q = parser.parse(QueryParser.escape(query));
            TopDocs topDocs = searcher.search(q, k);
            List<Document.Builder> result = new ArrayList<>();
            for (var scoreDoc : topDocs.scoreDocs) {
                org.apache.lucene.document.Document doc = searcher.doc(scoreDoc.doc);
                var id = Integer.parseInt(doc.get("id"));
                result.add(Document.newBuilder().setId(id).setFaissScore(0f).setLuceneScore(scoreDoc.score));
            }
//            System.out.printf("Got %s docs from lucene shard %s%n", topDocs.scoreDocs.length, shardId);

            overallSearchDocTime.addAndGet(System.currentTimeMillis() - start);
            overallSearchDocCounter.incrementAndGet();
            return result;
        } catch (Exception e) {
            throw new CompletionException("Failed to find docs in lucene shard %s".formatted(shardId), e);
        }
    }

    @Override
    public List<Document> enrichWithSimilarityScores(List<Document.Builder> docs, String query) {
        var start = System.currentTimeMillis();

        if (docs.isEmpty()) {
            System.out.println("lucene similarity stage was initiated for empty docs, strange");
            return List.of();
        }
//        System.out.printf("Searching similarity scores in lucene shard %s, for %s docs%n", shardId, docs.size());
        try {
            Query q = parser.parse(QueryParser.escape(query));
            IntSet idSet = new IntOpenHashSet(docs.size());
            for (var doc : docs) {
                idSet.add(doc.getId());
            }
            Query idFilter = IntPoint.newSetQuery("id", idSet.toIntArray());
            BooleanQuery combinedQuery = new BooleanQuery.Builder()
                    .add(q, BooleanClause.Occur.MUST)
                    .add(idFilter, BooleanClause.Occur.FILTER)
                    .build();

            TopDocs topDocs = searcher.search(combinedQuery, docs.size());
            Int2FloatOpenHashMap idToScore = new Int2FloatOpenHashMap(docs.size());
            for (var scoreDoc : topDocs.scoreDocs) {
                idToScore.put(Integer.parseInt(searcher.doc(scoreDoc.doc).get("id")), scoreDoc.score);
            }
//            System.out.printf("Got similarity scores from lucene shard %s with ids: %s%n", shardId, idToScore.keySet());
//            System.out.printf("Got %s similarity scores from lucene shard %s%n", idToScore.size(), shardId);

            var result = docs.stream().map(d -> d.setLuceneScore(idToScore.get(d.getId())).build()).toList();
            overallSimilarityScoresTime.addAndGet(System.currentTimeMillis() - start);
            overallSimilarityScoresCounter.incrementAndGet();
            return result;
        } catch (Exception e) {
            throw new CompletionException("Failed to get similarity scores in lucene shard %s".formatted(shardId), e);
        }
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

