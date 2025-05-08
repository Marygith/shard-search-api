package ru.nms.diplom.shardsearch.shard;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
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
    private final List<LeafReaderContext> leaves;
    private final int[] docBases;
    private final AtomicLong overallSearchDocTime = new AtomicLong(0);
    private final AtomicLong overallSimilarityScoresTime = new AtomicLong(0);
    private final AtomicInteger overallSearchDocCounter = new AtomicInteger(0);
    private final AtomicInteger overallSimilarityScoresCounter = new AtomicInteger(0);

    public LuceneShard(Path indexPath, int shardId) throws IOException {
        this.shardId = shardId;
        IndexReader reader = DirectoryReader.open(FSDirectory.open(indexPath));
        this.searcher = new IndexSearcher(reader);
        this.parser = new QueryParser("contents", new StandardAnalyzer());
        this.leaves = reader.leaves();
        this.docBases = leaves.stream().mapToInt(ctx -> ctx.docBase).toArray();
    }

    @Override
    public List<Document.Builder> searchDocs(String query, int k, List<Float> encodedQuery) {
        long start = System.currentTimeMillis();
        try {
            Query q = parser.parse(QueryParser.escape(query));
            TopDocs topDocs = searcher.search(q, k);

            List<Document.Builder> result = new ArrayList<>(topDocs.scoreDocs.length);
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                int globalDocId = scoreDoc.doc;
                int leafIndex = findLeafIndex(globalDocId);
                LeafReaderContext leaf = leaves.get(leafIndex);
                int localDocId = globalDocId - leaf.docBase;

                // Use DocValues to retrieve external ID
                NumericDocValues docValues = leaf.reader().getNumericDocValues("id");
                if (docValues != null && docValues.advanceExact(localDocId)) {
                    int id = (int) docValues.longValue();
                    result.add(Document.newBuilder()
                            .setId(id)
                            .setLuceneScore(scoreDoc.score)
                            .setFaissScore(0f)); // Will be filled later
                }

            }
            // 6. Track timing
            overallSearchDocTime.addAndGet(System.currentTimeMillis() - start);
            overallSearchDocCounter.incrementAndGet();

            return result;

        } catch (Exception e) {
            throw new CompletionException("Failed to find docs in Lucene shard %s".formatted(shardId), e);
        }
    }

    @Override
    public List<Document> enrichWithSimilarityScores(List<Document.Builder> docs, String query, List<Float> encodedQuery) {
        var start = System.currentTimeMillis();

        try {
            Query q = parser.parse(QueryParser.escape(query));

            int[] ids = docs.stream().mapToInt(Document.Builder::getId).toArray();
            Query idFilter = IntPoint.newSetQuery("id", ids);

            BooleanQuery combinedQuery = new BooleanQuery.Builder()
                    .add(q, BooleanClause.Occur.MUST)
                    .add(idFilter, BooleanClause.Occur.FILTER)
                    .build();

            TopDocs topDocs = searcher.search(combinedQuery, docs.size());
            Int2FloatOpenHashMap idToScore = new Int2FloatOpenHashMap(docs.size());

            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                int globalDocId = scoreDoc.doc;
                int leafIndex = findLeafIndex(globalDocId);
                LeafReaderContext leaf = leaves.get(leafIndex);
                int localDocId = globalDocId - leaf.docBase;

                NumericDocValues docValues = leaf.reader().getNumericDocValues("id");
                if (docValues != null && docValues.advanceExact(localDocId)) {
                    int id = (int) docValues.longValue();
                    idToScore.put(id, scoreDoc.score);
                }
            }

            var result = docs.stream()
                    .map(d -> d.setLuceneScore(idToScore.getOrDefault(d.getId(), 0f)).build())
                    .toList();
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

    private int findLeafIndex(int globalDocId) {
        int low = 0, high = docBases.length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int base = docBases[mid];
            int nextBase = (mid + 1 < docBases.length) ? docBases[mid + 1] : Integer.MAX_VALUE;

            if (globalDocId >= base && globalDocId < nextBase) return mid;
            if (globalDocId < base) high = mid - 1;
            else low = mid + 1;
        }
        throw new IllegalStateException("No leaf found for global doc ID: " + globalDocId);
    }
}

