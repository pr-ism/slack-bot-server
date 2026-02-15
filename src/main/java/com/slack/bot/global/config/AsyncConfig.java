package com.slack.bot.global.config;

import com.slack.bot.global.config.properties.ReviewInteractionAsyncProperties;
import com.slack.bot.global.config.properties.SlackEventAsyncProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@EnableAsync
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({
        SlackEventAsyncProperties.class,
        ReviewInteractionAsyncProperties.class
})
public class AsyncConfig implements AsyncConfigurer {

    private final SlackEventAsyncProperties slackEventAsyncProperties;
    private final ReviewInteractionAsyncProperties reviewInteractionAsyncProperties;

    @Bean(name = "slackEventExecutor")
    public TaskExecutor slackEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(slackEventAsyncProperties.corePoolSize());
        executor.setMaxPoolSize(slackEventAsyncProperties.maxPoolSize());
        executor.setThreadNamePrefix(slackEventAsyncProperties.threadNamePrefix());
        executor.setQueueCapacity(slackEventAsyncProperties.queueCapacity());
        executor.initialize();
        return executor;
    }

    @Bean(name = "reviewInteractionExecutor")
    public TaskExecutor reviewInteractionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(reviewInteractionAsyncProperties.corePoolSize());
        executor.setMaxPoolSize(reviewInteractionAsyncProperties.maxPoolSize());
        executor.setThreadNamePrefix(reviewInteractionAsyncProperties.threadNamePrefix());
        executor.setQueueCapacity(reviewInteractionAsyncProperties.queueCapacity());
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                log.info("Async Exception : {} {}", ex.getClass().getSimpleName(), ex.getMessage());
    }
}
