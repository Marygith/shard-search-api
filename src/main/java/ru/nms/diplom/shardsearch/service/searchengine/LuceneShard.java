package ru.nms.diplom.shardsearch.service.searchengine;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import ru.nms.diplom.shardsearch.Document;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletionException;

public class LuceneShard implements SearchEngine {
    private final IndexSearcher searcher;
    private final QueryParser parser;
    private final int shardId;

    public LuceneShard(Path indexPath, int shardId) throws IOException {
        this.shardId = shardId;
        IndexReader reader = DirectoryReader.open(FSDirectory.open(indexPath));
        this.searcher = new IndexSearcher(reader);
        this.parser = new QueryParser("contents", new StandardAnalyzer());
    }

    @Override
    public List<Document.Builder> searchDocs(String query, int k) {

        System.out.println("Searching docs in lucene shard " + shardId);

        try {
            Query q = parser.parse(QueryParser.escape(query));
            TopDocs topDocs = searcher.search(q, k);
            List<Document.Builder> result = new ArrayList<>();
            for (var scoreDoc : topDocs.scoreDocs) {
                org.apache.lucene.document.Document doc = searcher.doc(scoreDoc.doc);
                var id = Integer.parseInt(doc.get("id"));
                result.add(Document.newBuilder().setId(id).setFaissScore(0f).setLuceneScore(scoreDoc.score));
            }
            System.out.printf("Got docs from lucene shard %s with ids: %s%n", shardId, result.stream().map(Document.Builder::getId).toList());

            return result;
        } catch (Exception e) {
            throw new CompletionException("Failed to find docs in lucene shard %s".formatted(shardId), e);
        }
    }

    @Override
    public List<Document> enrichWithSimilarityScores(List<Document.Builder> docs, String query) {
        if (docs.isEmpty()) {
            return List.of();
        }
        System.out.printf("Searching similarity scores in lucene shard %s, docs ids: %s%n", shardId, docs.stream().map(Document.Builder::getId).toList());
        try {
            Query q = parser.parse(QueryParser.escape(query));
            BooleanQuery.Builder idFilterBuilder = new BooleanQuery.Builder();
            for (var doc : docs) {
                TermQuery idQuery = new TermQuery(new Term("id", String.valueOf(doc.getId())));
                idFilterBuilder.add(idQuery, BooleanClause.Occur.SHOULD); // Match any of the ids
            }

            BooleanQuery idFilter = idFilterBuilder.build();
            BooleanQuery combinedQuery = new BooleanQuery.Builder()
                    .add(q, BooleanClause.Occur.MUST)
                    .add(idFilter, BooleanClause.Occur.FILTER)
                    .build();

            TopDocs topDocs = searcher.search(combinedQuery, docs.size());
            Int2FloatOpenHashMap idToScore = new Int2FloatOpenHashMap(docs.size());
            for (var scoreDoc : topDocs.scoreDocs) {
                idToScore.put(Integer.parseInt(searcher.doc(scoreDoc.doc).get("id")), scoreDoc.score);
            }
            System.out.printf("Got similarity scores from lucene shard %s with ids: %s%n", shardId, idToScore.keySet());

            return docs.stream().map(d -> d.setLuceneScore(idToScore.get(d.getId())).build()).toList();
        } catch (Exception e) {
            throw new CompletionException("Failed to get similarity scores in lucene shard %s".formatted(shardId), e);
        }
    }
}

