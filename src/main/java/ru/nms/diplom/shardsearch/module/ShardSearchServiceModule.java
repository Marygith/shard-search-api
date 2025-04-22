package ru.nms.diplom.shardsearch.module;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import ru.nms.diplom.shardsearch.temp.FaissScoreEnricher;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ShardSearchServiceModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(Executor.class).toInstance(Executors.newFixedThreadPool(16));
    }

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