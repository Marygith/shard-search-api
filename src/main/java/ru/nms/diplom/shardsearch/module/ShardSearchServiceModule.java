package ru.nms.diplom.shardsearch.module;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import ru.nms.diplom.shardsearch.factory.LuceneShardFactory;
import ru.nms.diplom.shardsearch.factory.ProxyShardBuilder;
import ru.nms.diplom.shardsearch.service.engine.StubManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ShardSearchServiceModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ProxyShardBuilder.class).in(Singleton.class);
        bind(LuceneShardFactory.class).in(Singleton.class);
        bind(StubManager.class).in(Singleton.class);

    }

    @Provides
    @Singleton
    public ExecutorService provideExecutorService() {
        return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }
}