package com.slack.bot.global.config;

import static org.slf4j.Logger.ROOT_LOGGER_NAME;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.filter.ThresholdFilter;
import com.slack.bot.global.log.SlackAppender;
import java.time.Clock;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Profile("slack-error-logging")
@Configuration
public class LogConfig {

    private static final String SLACK_APPENDER_NAME = "SLACK_APPENDER";
    private static final String ASYNC_SLACK_APPENDER_NAME = "ASYNC_SLACK_APPENDER";
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Bean
    public RestClient slackWebhookRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));

        return RestClient.builder()
                         .requestFactory(factory)
                         .build();
    }

    @Bean
    public DateTimeFormatter slackAppenderDateTimeFormatter(Clock clock) {
        return DateTimeFormatter.ofPattern(DATE_TIME_PATTERN)
                                .withZone(clock.getZone());
    }

    @Bean
    public SlackAppender slackAppender(
            RestClient slackWebhookRestClient,
            DateTimeFormatter slackAppenderDateTimeFormatter,
            @Value("${WEB_HOOK:}") String webHook,
            @Value("${PROFILE:}") String profile
    ) {
        return new SlackAppender(slackWebhookRestClient, slackAppenderDateTimeFormatter, webHook, profile);
    }

    @Bean
    public SmartInitializingSingleton slackAppenderRegistrar(SlackAppender slackAppender) {
        return () -> registerSlackAppender(slackAppender);
    }

    private void registerSlackAppender(SlackAppender slackAppender) {
        Object loggerFactory = LoggerFactory.getILoggerFactory();
        if (!(loggerFactory instanceof LoggerContext context)) {
            LoggerFactory.getLogger(LogConfig.class)
                         .error("ILoggerFactory가 LoggerContext가 아닙니다. SlackAppender 등록을 건너뜁니다.");
            return;
        }
        Logger rootLogger = context.getLogger(ROOT_LOGGER_NAME);

        if (rootLogger.getAppender(ASYNC_SLACK_APPENDER_NAME) != null) {
            rootLogger.detachAppender(ASYNC_SLACK_APPENDER_NAME);
        }
        if (rootLogger.getAppender(SLACK_APPENDER_NAME) != null) {
            rootLogger.detachAppender(SLACK_APPENDER_NAME);
        }

        slackAppender.setContext(context);
        slackAppender.setName(SLACK_APPENDER_NAME);
        slackAppender.start();

        if (!slackAppender.isStarted()) {
            return;
        }

        AsyncAppender asyncSlackAppender = new AsyncAppender();
        asyncSlackAppender.setContext(context);
        asyncSlackAppender.setName(ASYNC_SLACK_APPENDER_NAME);
        asyncSlackAppender.setQueueSize(256);
        asyncSlackAppender.setNeverBlock(true);

        ThresholdFilter thresholdFilter = new ThresholdFilter();
        thresholdFilter.setLevel("ERROR");
        thresholdFilter.start();
        asyncSlackAppender.addFilter(thresholdFilter);
        asyncSlackAppender.addAppender(slackAppender);
        asyncSlackAppender.start();

        rootLogger.addAppender(asyncSlackAppender);
    }
}
