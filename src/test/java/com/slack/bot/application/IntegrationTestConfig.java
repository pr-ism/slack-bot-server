package com.slack.bot.application;

import com.slack.bot.application.command.client.MemberConnectionSlackApiClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@TestConfiguration
public class IntegrationTestConfig {

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
}
