package ru.nms.diplom.shardsearch.service.searchengine;

import com.google.inject.Singleton;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Singleton
public class LuceneShardFactory {

    public LuceneShard initializeShard(int shardId, Path indexPath, Path csvPath) throws IOException {
        System.out.println("attempting to initialize lucene shard " + shardId);
        if (!Files.exists(indexPath) || isDirectoryEmpty(indexPath)) {
            buildLuceneIndex(indexPath, csvPath);
        }
        System.out.printf("lucene index for path: %s already exists%n", indexPath );
        return new LuceneShard(indexPath, shardId);
    }

    private boolean isDirectoryEmpty(Path dir) throws IOException {
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(dir)) {
            return !dirStream.iterator().hasNext();
        }
    }

    private void buildLuceneIndex(Path indexDir, Path csvFile) throws IOException {
        System.out.println("starting to build lucene index for path: " + indexDir);
        Files.createDirectories(indexDir);
        Directory luceneDir = FSDirectory.open(indexDir);
        StandardAnalyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        try (IndexWriter writer = new IndexWriter(luceneDir, config)) {
            try (BufferedReader reader = Files.newBufferedReader(csvFile)) {
                reader.readLine(); // skip header
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] args = line.split("\\^");
                    Document doc = new Document();
                    doc.add(new StringField("id", args[1], Field.Store.YES));
                    doc.add(new TextField("contents", args[0].replaceAll("\"", ""), Field.Store.YES));
                    writer.addDocument(doc);
                }
            }
        }
        System.out.println("finished building lucene index for path: " + indexDir);
    }
}
