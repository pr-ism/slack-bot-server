package com.slack.bot.application.interactivity.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;

import com.slack.bot.application.IntegrationTest;
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
class ClaimMappingWorkflowTest {

    @Autowired
    ClaimMappingWorkflow claimMappingWorkflow;

    @Autowired
    ProjectMemberRepository projectMemberRepository;

    @Autowired
    NotificationApiClient notificationApiClient;

    @Test
    @Sql("classpath:sql/fixtures/notification/workspace_t1.sql")
    void 신규_멤버_깃허브_ID_클레임이_성공하면_연동을_생성하고_에페메랄을_보낸다() {
        // when
        claimMappingWorkflow.handle("T1", "U3", "new-github-id", "xoxb-test-token", "C1");

        // then
        Optional<ProjectMember> actual = projectMemberRepository.findBySlackUser("T1", "U3");

        assertAll(
                () -> assertThat(actual).isPresent(),
                () -> assertThat(actual.get().getGithubId().getValue()).isEqualTo("new-github-id"),
                () -> verify(notificationApiClient).sendEphemeralMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        eq("U3"),
                        argThat(message -> message.contains("new-github-id"))
                ),
                () -> verify(notificationApiClient, never()).openDirectMessageChannel("xoxb-test-token", "U3"),
                () -> verify(notificationApiClient, never()).sendMessage(any(), any(), any())
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/notification/project_member_t1.sql",
            "classpath:sql/fixtures/notification/notification_settings_t1_u1_dm_enabled.sql"
    })
    void 이미_다른_깃허브_ID로_등록되어_있어도_기존_매핑_ID로_이미_등록됨_메시지를_보낸다() {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1")).willReturn("D1");

        // when
        claimMappingWorkflow.handle("T1", "U1", "new-github-id", "xoxb-test-token", "C1");

        // then
        Optional<ProjectMember> actual = projectMemberRepository.findBySlackUser("T1", "U1");

        assertAll(
                () -> assertThat(actual).isPresent(),
                () -> assertThat(actual.get().getGithubId().getValue()).isEqualTo("user1-gh"),
                () -> verify(notificationApiClient).sendEphemeralMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        eq("U1"),
                        argThat(message -> message.contains("이미 GitHub ID가 등록") && message.contains("user1-gh"))
                ),
                () -> verify(notificationApiClient).openDirectMessageChannel("xoxb-test-token", "U1"),
                () -> verify(notificationApiClient).sendMessage(
                        eq("xoxb-test-token"),
                        eq("D1"),
                        argThat(message -> message.contains("이미 GitHub ID가 등록") && message.contains("user1-gh"))
                ),
                () -> verify(notificationApiClient, never()).sendEphemeralMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        eq("U1"),
                        argThat(message -> message.contains("✅ 등록되었습니다"))
                )
        );
    }
}
