package com.zkp.my12306.ntc.llm.stream;

import com.alibaba.ttl.threadpool.TtlExecutors;
import com.zkp.my12306.ntc.llm.config.AIModelProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class StreamAsyncExecutor {
    private final ThreadPoolExecutor rawExecutor;
    private final ExecutorService executorService;

    public StreamAsyncExecutor(AIModelProperties properties) {
        AIModelProperties.StreamExecutor config = properties.getStreamExecutor();
        int coreSize = positive(config.getCoreSize(), "ai.stream-executor.core-size");
        int maxSize = positive(config.getMaxSize(), "ai.stream-executor.max-size");
        int queueCapacity = positive(config.getQueueCapacity(), "ai.stream-executor.queue-capacity");
        int keepAliveSeconds = positive(config.getKeepAliveSeconds(), "ai.stream-executor.keep-alive-seconds");
        if (maxSize < coreSize) {
            throw new IllegalStateException("ai.stream-executor.max-size 不能小于 core-size");
        }
        String threadPrefix = config.getThreadNamePrefix() == null || config.getThreadNamePrefix().isBlank()
                ? "llm-stream-" : config.getThreadNamePrefix();
        this.rawExecutor = new ThreadPoolExecutor(
                coreSize,
                maxSize,
                keepAliveSeconds,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new NamedThreadFactory(threadPrefix),
                rejectionPolicy(config.getRejectionPolicy()));
        this.executorService = TtlExecutors.getTtlExecutorService(rawExecutor);
    }

    public CompletableFuture<Void> execute(Runnable task) {
        return CompletableFuture.runAsync(task, executorService);
    }

    @PreDestroy
    public void shutdown() {
        rawExecutor.shutdown();
    }

    private int positive(int value, String field) {
        if (value <= 0) {
            throw new IllegalStateException(field + " 必须大于 0");
        }
        return value;
    }

    private RejectedExecutionHandler rejectionPolicy(String policy) {
        String resolved = policy == null ? "" : policy.trim().toLowerCase(Locale.ROOT);
        switch (resolved) {
            case "abort":
                return new ThreadPoolExecutor.AbortPolicy();
            case "discard":
                return new ThreadPoolExecutor.DiscardPolicy();
            case "discard-oldest":
                return new ThreadPoolExecutor.DiscardOldestPolicy();
            case "caller-runs":
            default:
                return new ThreadPoolExecutor.CallerRunsPolicy();
        }
    }

    private static final class NamedThreadFactory implements java.util.concurrent.ThreadFactory {
        private final String prefix;
        private final AtomicInteger index = new AtomicInteger(1);

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + index.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        }
    }
}
