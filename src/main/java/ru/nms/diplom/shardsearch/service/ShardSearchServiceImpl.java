package ru.nms.diplom.shardsearch.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.stub.StreamObserver;
import ru.nms.diplom.shardsearch.*;
import ru.nms.diplom.shardsearch.model.IndexType;
import ru.nms.diplom.shardsearch.service.searchengine.ProxyShardBuilder;
import ru.nms.diplom.shardsearch.service.searchengine.SearchEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

@Singleton
public class ShardSearchServiceImpl extends ShardSearchServiceGrpc.ShardSearchServiceImplBase {

    private final Map<Integer, SearchEngine> faissShards = new ConcurrentHashMap<>();
    private final Map<Integer, SearchEngine> luceneShards = new ConcurrentHashMap<>();
//    private final FaissScoreEnricher faissScoreEnricher;
//    private final LuceneScoreEnricher luceneScoreEnricher;
    private final ExecutorService executor;
    private final ProxyShardBuilder proxyShardBuilder;

    @Inject
    public ShardSearchServiceImpl(
//            Map<Integer, SearchEngine> faissShards,
//                                  Map<Integer, SearchEngine> luceneShards,
//                                  FaissScoreEnricher faissScoreEnricher,
//                                  LuceneScoreEnricher luceneScoreEnricher,
                                  ExecutorService executor, ProxyShardBuilder proxyShardBuilder) {
//        this.faissShards = faissShards;
//        this.luceneShards = luceneShards;
//        this.faissScoreEnricher = faissScoreEnricher;
//        this.luceneScoreEnricher = luceneScoreEnricher;
        this.executor = executor;
        this.proxyShardBuilder = proxyShardBuilder;
    }

    @Override
    public void shardSearch(ShardSearchRequest request, StreamObserver<ShardSearchResponse> responseObserver) {
        List<Integer> shardIds = request.getShardIdsList();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        IndexType indexType = IndexType.fromNumber(request.getIndexType());
        for (Integer shardId : shardIds) {
            var searchDocsSearchEngine = switch (indexType) {
                case VECTOR -> faissShards.get(shardId);
                case LUCENE -> luceneShards.get(shardId);
            };
            if (searchDocsSearchEngine == null) {
                addFailedResult(new RuntimeException("Shard with id %s of type %s was absent on the node".formatted(shardId, indexType.name())), responseObserver);
            }
            var getSimilarityScoresEngine = switch (indexType) {
                case LUCENE -> faissShards.computeIfAbsent(shardId, proxyShardBuilder::buildFaissProxyShard);
                case VECTOR -> luceneShards.computeIfAbsent(shardId, proxyShardBuilder::buildLuceneProxyShard);
            };
            var searchTask = createSearchDocsTask(request.getQuery(), request.getK(), searchDocsSearchEngine, getSimilarityScoresEngine, responseObserver);
            futures.add(searchTask);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((ignored, throwable) -> {
                    responseObserver.onCompleted();
                });
    }

    @Override
    public void getSimilarityScores(SimilarityScoresRequest request, StreamObserver<ShardSearchResponse> responseObserver) {

        IndexType indexType = IndexType.fromNumber(request.getIndexType());
        var similarityDocsEngine = switch (indexType) {
            case VECTOR -> faissShards.computeIfAbsent(request.getShardId(), proxyShardBuilder::buildFaissProxyShard);
            case LUCENE -> luceneShards.computeIfAbsent(request.getShardId(), proxyShardBuilder::buildLuceneProxyShard);
        };
        var docs = similarityDocsEngine.enrichWithSimilarityScores(request.getDocumentsList().stream().map(Document::toBuilder).toList(), request.getQuery());
        responseObserver.onNext(ShardSearchResponse.newBuilder().addAllResults(docs).build());
        responseObserver.onCompleted();
    }

    public void addFaissShard(SearchEngine faissShard, int shardId) {
        faissShards.put(shardId, faissShard);
    }

    public void addLuceneShard(SearchEngine luceneShard, int shardId) {
        luceneShards.put(shardId, luceneShard);
    }

//    private CompletableFuture<Void> createShardPipeline(ShardSearchRequest request,
//                                                        Integer shardId,
//                                                        StreamObserver<ShardSearchResponse> observer) {
//        IndexType indexType = IndexType.fromNumber(request.getIndexType());
//        String query = request.getQuery();
//        int k = request.getK();
//
//        switch (indexType) {
//            case VECTOR:
//                FaissShard faissShard = faissShards.get(shardId);
//                if (faissShard == null) return CompletableFuture.completedFuture(null);
//
//                return faissShard.searchDocuments(query, k)
//                        .thenComposeAsync(docs -> {
//                                return luceneScoreEnricher.enrichScores(docs, query, shardId)
//                                        .exceptionally(e -> docs); // fallback to original if enrich fails
//                        }, executor)
//                        .thenAcceptAsync(docs -> docs.forEach(observer::onNext), executor)
//                        .exceptionally(e -> {
//                            e.printStackTrace();
//                            return null;
//                        });
//
//            case LUCENE:
//                LuceneShard luceneShard = luceneShards.get(shardId);
//                if (luceneShard == null) return CompletableFuture.completedFuture(null);
//
//                return luceneShard.searchDocuments(query, k)
//                        .thenComposeAsync(docs -> {
//                            FaissShard faissShard2 = faissShards.get(shardId);
//                            if (faissShard2 != null) {
//                                return faissScoreEnricher.enrichScores(docs, query, shardId)
//                                        .exceptionally(e -> docs);
//                            } else {
//                                return CompletableFuture.completedFuture(docs);
//                            }
//                        }, executor)
//                        .thenAcceptAsync(docs -> docs.forEach(observer::onNext), executor)
//                        .exceptionally(e -> {
//                            e.printStackTrace();
//                            return null;
//                        });
//
//            default:
//                return CompletableFuture.completedFuture(null);
//        }
//    }

    private CompletableFuture<Void> createSearchDocsTask(
            String query,
            int k,
            SearchEngine searchDocsEngine,
            SearchEngine getSimilarityScoresEngine,
            StreamObserver<ShardSearchResponse> observer) {

        return CompletableFuture
                .supplyAsync(() -> searchDocsEngine.searchDocs(query, k), executor)
                .thenApply(docs -> getSimilarityScoresEngine.enrichWithSimilarityScores(docs, query))
                .thenApply(docs -> ShardSearchResponse.newBuilder().addAllResults(docs).build())
                .handle((response, throwable) -> {
                    if (throwable != null) {
                        addFailedResult(throwable, observer);
                    } else {
                        addResult(response, observer);
                    }
                    return null;
                });
    }

    private synchronized void addResult(ShardSearchResponse response, StreamObserver<ShardSearchResponse> observer) {
            observer.onNext(response);
    }

    private synchronized void addFailedResult(Throwable throwable, StreamObserver<ShardSearchResponse> observer) {
        observer.onError(throwable);
    }
}