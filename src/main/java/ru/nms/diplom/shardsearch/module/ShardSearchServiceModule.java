package ru.nms.diplom.shardsearch.module;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import ru.nms.diplom.shardsearch.config.ShardFileConfig;
import ru.nms.diplom.shardsearch.service.searchengine.LuceneShardFactory;
import ru.nms.diplom.shardsearch.service.searchengine.ProxyShardBuilder;
import ru.nms.diplom.shardsearch.service.searchengine.StubManager;
import ru.nms.diplom.shardsearch.temp.FaissScoreEnricher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ShardSearchServiceModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ProxyShardBuilder.class).in(Singleton.class);
        bind(LuceneShardFactory.class).in(Singleton.class);
        bind(StubManager.class).in(Singleton.class);

        // ShardFileConfig will be provided separately
    }

    @Provides
    @Singleton
    public ExecutorService provideExecutorService() {
        return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

//    @Provides
//    @Singleton
//    public ShardFileConfig provideShardFileConfig() {
//        LoaderOptions loaderOptions = new LoaderOptions();
//        Constructor constructor = new Constructor(ShardFileConfig.class, loaderOptions);
//        Yaml yaml = new Yaml(constructor);
//
//        try (InputStream input = Files.newInputStream(Paths.get("config/shards.yaml"))) {
//            ShardFileConfig config =  yaml.load(input);
//            System.out.println("loaded config: \n" + config);
//            return config;
//        } catch (IOException e) {
//            throw new RuntimeException("Failed to load shard file configuration", e);
//        }
//    }

//    @Provides
//    @Singleton
//    Map<Integer, SearchEngine> provideSearchEngines() {
//        Map<Integer, SearchEngine> map = new HashMap<>();
//        map.put(1, new LuceneShard(Paths.get("./index1"), shardId));
//        map.put(2, new FaissShard(ManagedChannelBuilder.forAddress("localhost", 50052).usePlaintext().build()));
//        return map;
//    }
//
//    @Provides
//    @Singleton
//    Map<IndexType, OldScoreEnricher> provideScoreEnrichers(FaissOldScoreEnricher fse, LuceneOldScoreEnricher lse) {
//        return Map.of(IndexType.LUCENE, lse, IndexType.VECTOR, fse);
//    }
//
//    @Provides
//    @Singleton
//    Map<Integer, PassageReader> providePassageReaders() {
//        Map<Integer, PassageReader> readers = new HashMap<>();
//        readers.put(1, new PassageReader("vectors1.csv"));
//        readers.put(2, new PassageReader("vectors2.csv"));
//        return readers;
//    }
}