package com.slack.bot.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.command.client.MemberConnectionSlackApiClient;
import com.slack.bot.application.event.client.SlackEventApiClient;
import com.slack.bot.application.event.dto.ChannelNameWrapper;
import com.slack.bot.application.review.box.ReviewNotificationSourceContext;
import com.slack.bot.application.review.box.out.ReviewNotificationOutboxEnqueuer;
import com.slack.bot.application.review.client.ReviewSlackApiClient;
import com.slack.bot.application.review.channel.ReviewSlackChannelResolver;
import com.slack.bot.infrastructure.common.MysqlDuplicateKeyDetector;
import com.slack.bot.infrastructure.interaction.box.in.repository.SlackInteractionInboxRepository;
import com.slack.bot.infrastructure.interaction.box.out.repository.SlackNotificationOutboxRepository;
import com.slack.bot.infrastructure.interaction.box.persistence.in.H2SlackInteractionInboxRepositoryAdapter;
import com.slack.bot.infrastructure.interaction.box.persistence.in.JpaSlackInteractionInboxHistoryRepository;
import com.slack.bot.infrastructure.interaction.box.persistence.in.JpaSlackInteractionInboxRepository;
import com.slack.bot.infrastructure.interaction.box.persistence.out.H2SlackNotificationOutboxRepositoryAdapter;
import com.slack.bot.infrastructure.interaction.box.persistence.out.JpaSlackNotificationOutboxRepository;
import com.slack.bot.infrastructure.review.batch.SpyReviewNotificationService;
import com.slack.bot.infrastructure.review.box.out.repository.ReviewNotificationOutboxRepository;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.infrastructure.review.box.in.repository.ReviewRequestInboxRepository;
import com.slack.bot.infrastructure.review.persistence.box.in.H2ReviewRequestInboxRepositoryAdapter;
import com.slack.bot.infrastructure.review.persistence.box.in.JpaReviewRequestInboxHistoryRepository;
import com.slack.bot.infrastructure.review.persistence.box.in.JpaReviewRequestInboxRepository;
import com.slack.bot.infrastructure.review.persistence.box.out.H2ReviewNotificationOutboxRepositoryAdapter;
import com.slack.bot.infrastructure.review.persistence.box.out.JpaReviewNotificationOutboxHistoryRepository;
import com.slack.bot.infrastructure.review.persistence.box.out.JpaReviewNotificationOutboxRepository;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.client.RestClient;

@TestConfiguration
public class IntegrationTestConfig {

    public static final String ERROR_CHANNEL_NAME = "error-channel-id";

    @Bean
    @Primary
    public MemberConnectionSlackApiClient memberConnectionSlackApiClient() {
        String baseUrl = "https://slack.com/api/";
        RestClient slackClient = RestClient.builder()
                                           .baseUrl(baseUrl)
                                           .build();

        return new MemberConnectionSlackApiClient(slackClient) {

            @Override
            public String resolveUserName(String token, String slackUserId) {
                return "신규 사용자";
            }
        };
    }

    @Bean
    @Primary
    public SlackEventApiClient slackEventApiClient() {
        String baseUrl = "https://slack.com/api/";
        RestClient slackClient = RestClient.builder()
                                           .baseUrl(baseUrl)
                                           .build();

        return new SlackEventApiClient(slackClient) {

            @Override
            public ChannelNameWrapper fetchChannelInfo(String token, String channelId) {
                if ("error-channel-id".equals(channelId)) {
                    throw new RuntimeException("테스트를 위한 실패");
                }

                return new ChannelNameWrapper("integration-test-channel");
            }

            @Override
            public void sendMessage(String token, String channelId, String text) {
            }

            @Override
            public void sendEphemeralMessage(String token, String channelId, String targetUserId, String text) {
            }
        };
    }

    @Bean
    @Primary
    public ReviewSlackApiClient reviewSlackApiClient() {
        RestClient slackClient = RestClient.builder()
                                           .baseUrl("https://slack.com/api/")
                                           .build();
        ObjectMapper objectMapper = new ObjectMapper();

        return new ReviewSlackApiClient(slackClient, objectMapper) {

            @Override
            public void sendBlockMessage(
                    String token,
                    String channelId,
                    JsonNode blocks,
                    JsonNode attachments,
                    String fallbackText
            ) {
            }
        };
    }

    @Bean
    @Primary
    public SpyReviewNotificationService spyReviewNotificationService(
            ReviewNotificationOutboxEnqueuer reviewNotificationOutboxEnqueuer,
            ReviewSlackChannelResolver channelResolver,
            ReviewNotificationSourceContext reviewNotificationSourceContext
    ) {
        return new SpyReviewNotificationService(
                reviewNotificationOutboxEnqueuer,
                channelResolver,
                reviewNotificationSourceContext
        );
    }

    @Bean
    @Primary
    public MysqlDuplicateKeyDetector mysqlDuplicateKeyDetector() {
        return new MysqlDuplicateKeyDetector() {

            @Override
            public boolean isDuplicateKey(Throwable throwable) {
                if (isH2DuplicateKey(throwable)) {
                    return true;
                }
                return super.isDuplicateKey(throwable);
            }

            private boolean isH2DuplicateKey(Throwable throwable) {
                Throwable current = throwable;

                while (current != null) {
                    if (current instanceof ConstraintViolationException cve && isH2SqlState(cve.getSQLException())) {
                        return true;
                    }

                    if (current instanceof SQLException sqlException && isH2SqlState(sqlException)) {
                        return true;
                    }

                    current = current.getCause();
                }

                return false;
            }

            private boolean isH2SqlState(SQLException sqlException) {
                if (sqlException == null) {
                    return false;
                }
                return "23505".equals(sqlException.getSQLState());
            }
        };
    }

    @Bean
    @Primary
    public SlackInteractionInboxRepository slackInteractionInboxRepository(
            JPAQueryFactory queryFactory,
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            JpaSlackInteractionInboxRepository repository,
            JpaSlackInteractionInboxHistoryRepository historyRepository
    ) {
        return new H2SlackInteractionInboxRepositoryAdapter(
                queryFactory,
                namedParameterJdbcTemplate,
                repository,
                historyRepository
        );
    }

    @Bean
    @Primary
    public SlackNotificationOutboxRepository slackNotificationOutboxRepository(
            JPAQueryFactory queryFactory,
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            JpaSlackNotificationOutboxRepository repository
    ) {
        return new H2SlackNotificationOutboxRepositoryAdapter(
                queryFactory,
                namedParameterJdbcTemplate,
                repository
        );
    }

    @Bean
    @Primary
    public ReviewRequestInboxRepository reviewRequestInboxRepository(
            JPAQueryFactory queryFactory,
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            JpaReviewRequestInboxRepository repository,
            JpaReviewRequestInboxHistoryRepository historyRepository
    ) {
        return new H2ReviewRequestInboxRepositoryAdapter(
                queryFactory,
                namedParameterJdbcTemplate,
                repository,
                historyRepository
        );
    }

    @Bean
    @Primary
    public ReviewNotificationOutboxRepository reviewNotificationOutboxRepository(
            JPAQueryFactory queryFactory,
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            JpaReviewNotificationOutboxRepository repository,
            JpaReviewNotificationOutboxHistoryRepository historyRepository
    ) {
        return new H2ReviewNotificationOutboxRepositoryAdapter(
                queryFactory,
                namedParameterJdbcTemplate,
                repository,
                historyRepository
        );
    }
}
