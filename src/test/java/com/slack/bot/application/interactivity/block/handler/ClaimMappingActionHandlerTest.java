package com.slack.bot.application.interactivity.block.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interactivity.block.BlockActionType;
import com.slack.bot.application.interactivity.block.dto.BlockActionCommandDto;
import com.slack.bot.application.interactivity.block.dto.BlockActionOutcomeDto;
import com.slack.bot.application.interactivity.client.NotificationApiClient;
import com.slack.bot.domain.member.ProjectMember;
import com.slack.bot.domain.member.repository.ProjectMemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ClaimMappingActionHandlerTest {

    @Autowired
    ClaimMappingActionHandler claimMappingActionHandler;

    @Autowired
    ProjectMemberRepository projectMemberRepository;

    @Autowired
    NotificationApiClient notificationApiClient;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/notification/project_member_t1.sql",
            "classpath:sql/fixtures/notification/notification_settings_t1_u1_dm_enabled.sql"
    })
    void 깃허브_ID_클레임_액션이면_멤버_연동을_갱신하고_에페메랄과_DM을_보낸다() {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1")).willReturn("D1");
        BlockActionCommandDto command = claimCommandWithGithubId("new-github-id");

        // when
        BlockActionOutcomeDto actual = claimMappingActionHandler.handle(command);

        // then
        Optional<ProjectMember> mapped = projectMemberRepository.findBySlackUser("T1", "U1");

        assertAll(
                () -> assertThat(actual.duplicateReservation()).isNull(),
                () -> assertThat(actual.cancelledReservation()).isNull(),
                () -> assertThat(mapped).isPresent(),
                () -> assertThat(mapped.get().getGithubId().getValue()).isEqualTo("new-github-id"),
                () -> verify(notificationApiClient).sendEphemeralMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        eq("U1"),
                        argThat(message -> message.contains("new-github-id"))
                ),
                () -> verify(notificationApiClient).openDirectMessageChannel("xoxb-test-token", "U1"),
                () -> verify(notificationApiClient).sendMessage(
                        eq("xoxb-test-token"),
                        eq("D1"),
                        argThat(message -> message.contains("new-github-id"))
                )
        );
    }

    private BlockActionCommandDto claimCommandWithGithubId(String githubId) {
        JsonNode action = objectMapper.createObjectNode()
                .put("value", githubId);

        return new BlockActionCommandDto(
                objectMapper.createObjectNode(),
                action,
                "claim_" + githubId,
                BlockActionType.CLAIM_PREFIX,
                "T1",
                "C1",
                "U1",
                "xoxb-test-token"
        );
    }
}
