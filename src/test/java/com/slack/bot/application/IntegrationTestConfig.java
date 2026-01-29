package com.slack.bot.application;

import com.slack.bot.application.command.client.MemberConnectionSlackApiClient;
import com.slack.bot.application.event.client.SlackEventApiClient;
import com.slack.bot.application.event.dto.ChannelNameWrapper;
import com.slack.bot.infrastructure.common.MysqlDuplicateKeyDetector;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
}
