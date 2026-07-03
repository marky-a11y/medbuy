package com.autoresolve.mediabuying.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

/**
 * Configuration for Spring {@link org.springframework.context.event.EventListener}
 * asynchronous dispatch.
 *
 * <p>Provides a dedicated {@link ThreadPoolTaskExecutor} for processing integration
 * events (KPI ingestion, cache invalidation, data refresh) without blocking the
 * publisher thread. This replaces the earlier Kafka-based messaging topology.</p>
 *
 * <p>Graceful shutdown is configured with a 30-second await termination period
 * so that in-flight events complete before the application context closes.</p>
 */
@Configuration
@EnableAsync
public class SpringEventConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SpringEventConfig.class);

    /**
     * The primary executor for {@code @Async} event listener methods.
     * <ul>
     *   <li>Core pool: 4 threads — sufficient for parallel ingestion of 5 platforms</li>
     *   <li>Max pool: 10 threads — handles bursts during scheduled refreshes</li>
     *   <li>Queue: 100 tasks — prevents rejection under moderate load</li>
     *   <li>CallerRunsPolicy — throttles publishers if all threads and queue are saturated</li>
     * </ul>
     */
    @Bean(name = "eventTaskExecutor")
    public ThreadPoolTaskExecutor eventTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("event-async-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return eventTaskExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new AsyncUncaughtExceptionHandler() {
            @Override
            public void handleUncaughtException(Throwable ex, Method method, Object... params) {
                log.error("Uncaught async exception in method '{}': {}", method.getName(), ex.getMessage(), ex);
            }
        };
    }
}
