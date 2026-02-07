package com.slack.bot.application.interactivity.block.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
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
        BlockActionCommandDto command = claimCommandWithGithubId("new-github-id", "U1");

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

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/notification/project_member_t1.sql",
            "classpath:sql/fixtures/notification/notification_settings_t1_u2_dm_disabled.sql"
    })
    void DM_알림이_비활성화된_사용자의_클레임_매핑은_에페메랄만_보내고_DM은_생략한다() {
        // given
        BlockActionCommandDto command = claimCommandWithGithubId("updated-u2-github", "U2");

        // when
        BlockActionOutcomeDto actual = claimMappingActionHandler.handle(command);

        // then
        Optional<ProjectMember> mapped = projectMemberRepository.findBySlackUser("T1", "U2");

        assertAll(
                () -> assertThat(actual).isEqualTo(BlockActionOutcomeDto.empty()),
                () -> assertThat(mapped).isPresent(),
                () -> assertThat(mapped.get().getGithubId().getValue()).isEqualTo("updated-u2-github"),
                () -> verify(notificationApiClient).sendEphemeralMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        eq("U2"),
                        argThat(message -> message.contains("updated-u2-github"))
                ),
                () -> verify(notificationApiClient, never()).openDirectMessageChannel(any(), any()),
                () -> verify(notificationApiClient, never()).sendMessage(any(), any(), any())
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/notification/workspace_t1.sql")
    void 매핑_대상_멤버가_없으면_새_멤버를_생성하고_에페메랄_알림만_보낸다() {
        // given
        BlockActionCommandDto command = claimCommandWithGithubId("new-user-github", "U3");

        // when
        BlockActionOutcomeDto actual = claimMappingActionHandler.handle(command);

        // then
        Optional<ProjectMember> mapped = projectMemberRepository.findBySlackUser("T1", "U3");

        assertAll(
                () -> assertThat(actual).isEqualTo(BlockActionOutcomeDto.empty()),
                () -> assertThat(mapped).isPresent(),
                () -> assertThat(mapped.get().getGithubId().getValue()).isEqualTo("new-user-github"),
                () -> verify(notificationApiClient).sendEphemeralMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        eq("U3"),
                        argThat(message -> message.contains("new-user-github"))
                ),
                () -> verify(notificationApiClient, never()).openDirectMessageChannel(any(), any()),
                () -> verify(notificationApiClient, never()).sendMessage(any(), any(), any())
        );
    }

    private BlockActionCommandDto claimCommandWithGithubId(String githubId, String slackUserId) {
        JsonNode action = objectMapper.createObjectNode()
                .put("value", githubId);

        return new BlockActionCommandDto(
                objectMapper.createObjectNode(),
                action,
                "claim_" + githubId,
                BlockActionType.CLAIM_PREFIX,
                "T1",
                "C1",
                slackUserId,
                "xoxb-test-token"
        );
    }
}
