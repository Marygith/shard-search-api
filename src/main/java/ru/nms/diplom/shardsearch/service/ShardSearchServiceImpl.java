package ru.nms.diplom.shardsearch.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.stub.StreamObserver;
import ru.nms.diplom.clusterstate.service.NodeRequest;
import ru.nms.diplom.clusterstate.service.ShardConfig;
import ru.nms.diplom.clusterstate.service.ShardConfigList;
import ru.nms.diplom.faiss.FaissSearchServiceGrpc;
import ru.nms.diplom.shardsearch.*;
import ru.nms.diplom.shardsearch.factory.LuceneShardFactory;
import ru.nms.diplom.shardsearch.factory.ProxyShardBuilder;
import ru.nms.diplom.shardsearch.model.IndexType;
import ru.nms.diplom.shardsearch.service.scoreenricher.PassageReader;
import ru.nms.diplom.shardsearch.service.engine.*;
import ru.nms.diplom.shardsearch.shard.FaissShard;
import ru.nms.diplom.shardsearch.shard.LuceneShard;

import java.io.IOException;
import java.nio.file.Path;
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
    private final ExecutorService executor;
    private final ProxyShardBuilder proxyShardBuilder;
    private final StubManager stubManager;

    @Inject
    public ShardSearchServiceImpl(
            ExecutorService executor,
            ProxyShardBuilder proxyShardBuilder,
            LuceneShardFactory luceneShardFactory,
            StubManager stubManager) {
        this.executor = executor;
        this.proxyShardBuilder = proxyShardBuilder;
        this.stubManager = stubManager;
        initShards( luceneShardFactory);
    }

    private void initShards(LuceneShardFactory luceneShardFactory) {
        String nodeId = System.getenv("NODE_ID");

        var clusterStateStub = stubManager.getClusterStateStub();
        NodeRequest request = NodeRequest.newBuilder()
                .setNodeId(nodeId)
                .build();

        ShardConfigList shardConfigList = clusterStateStub.getShardsForNode(request);
        System.out.println("\nloaded config: " + shardConfigList);
        List<ShardConfig> assignedShards = shardConfigList.getShardsList();

        for (var entry : assignedShards) {
            int shardId = entry.getId();
            String type = entry.getType().toLowerCase();
            Path passageCsvFile = Path.of(entry.getPassagesCsvPath());
            Path embeddingsCsvFile = Path.of(entry.getEmbeddingsCsvPath());
            Path luceneIndexPath = Path.of(entry.getLuceneIndexPath());

            try {
                if (type.equals("lucene") || type.equals("both")) {
                    LuceneShard luceneShard = luceneShardFactory.initializeShard(shardId, luceneIndexPath, passageCsvFile);
                    luceneShards.put(shardId, luceneShard);
//                    System.out.printf("Initialized Lucene shard %d%n", shardId);
                }

                if (type.equals("faiss") || type.equals("both")) {
                    FaissSearchServiceGrpc.FaissSearchServiceBlockingStub stub = stubManager.getFaissStub(shardId);
                    PassageReader passageReader = new PassageReader(embeddingsCsvFile.toString(), 384);
                    FaissShard faissShard = new FaissShard(stub, passageReader, shardId);
                    faissShards.put(shardId, faissShard);
//                    System.out.printf("Initialized FAISS shard %d%n", shardId);
                }

            } catch (IOException e) {
                System.err.printf("Failed to initialize shard %d: %s%n", shardId, e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @Override
    public void shardSearch(ShardSearchRequest request, StreamObserver<ShardSearchResponse> responseObserver) {
//        System.out.println("came shard search request " + request);
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
//        System.out.println("came similarity scores request for type %s from another shard".formatted(indexType.name()) + request);

        var docs = similarityDocsEngine.enrichWithSimilarityScores(request.getDocumentsList().stream().map(Document::toBuilder).toList(), request.getQuery());
        responseObserver.onNext(ShardSearchResponse.newBuilder().addAllResults(docs).build());
        responseObserver.onCompleted();
    }

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
//        System.out.println("sending response back: " + response);
            observer.onNext(response);
    }

    private synchronized void addFailedResult(Throwable throwable, StreamObserver<ShardSearchResponse> observer) {
        System.out.println("sending error back: " + throwable);
        throwable.printStackTrace();
        observer.onError(throwable);
    }
}