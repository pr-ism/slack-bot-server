package com.slack.bot.application.interactivity.block.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interactivity.block.BlockActionType;
import com.slack.bot.application.interactivity.block.dto.BlockActionCommandDto;
import com.slack.bot.application.interactivity.block.dto.BlockActionOutcomeDto;
import com.slack.bot.application.interactivity.client.NotificationApiClient;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class StartReviewActionHandlerTest {

    @Autowired
    StartReviewActionHandler startReviewActionHandler;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NotificationApiClient notificationApiClient;

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/notification/project_member_t1.sql",
            "classpath:sql/fixtures/notification/notification_settings_t1_u1_dm_enabled.sql"
    })
    void 리뷰_바로_시작_액션이면_리뷰이_DM_설정이_켜져있을때_즉시_DM을_보낸다() {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1")).willReturn("D_AUTHOR");
        BlockActionCommandDto command = commandWithMeta(metaJson(), "U2");

        // when
        BlockActionOutcomeDto actual = startReviewActionHandler.handle(command);

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo(BlockActionOutcomeDto.empty()),
                () -> verify(notificationApiClient).sendEphemeralMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        eq("U2"),
                        eq("리뷰 시작 알림을 전송했습니다.")
                ),
                () -> verify(notificationApiClient).openDirectMessageChannel("xoxb-test-token", "U1"),
                () -> verify(notificationApiClient).sendMessage(
                        eq("xoxb-test-token"),
                        eq("D_AUTHOR"),
                        argThat(message -> message.contains("<@U1>") && message.contains("<@U2>"))
                ),
                () -> verify(notificationApiClient, never()).openDirectMessageChannel("xoxb-test-token", "U2")
        );
    }

    @Test
    void 메타가_없으면_리뷰_시작_DM을_보내지_않는다() {
        // given
        BlockActionCommandDto command = new BlockActionCommandDto(
                objectMapper.createObjectNode(),
                objectMapper.createObjectNode(),
                BlockActionType.START_REVIEW.value(),
                BlockActionType.START_REVIEW,
                "T1",
                "C1",
                "U2",
                "xoxb-test-token"
        );

        // when
        BlockActionOutcomeDto actual = startReviewActionHandler.handle(command);

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo(BlockActionOutcomeDto.empty()),
                () -> verify(notificationApiClient, never()).openDirectMessageChannel(any(), any()),
                () -> verify(notificationApiClient, never()).sendMessage(any(), any(), any())
        );
    }

    @Test
    void 리뷰이가_리뷰_바로_시작을_누르면_예약_불가_알림을_보내고_DM을_보내지_않는다() {
        // given
        BlockActionCommandDto command = commandWithMeta(metaJson(), "U1");

        // when
        BlockActionOutcomeDto actual = startReviewActionHandler.handle(command);

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo(BlockActionOutcomeDto.empty()),
                () -> verify(notificationApiClient).sendEphemeralMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        eq("U1"),
                        eq("리뷰이는 해당 PR에 대한 리뷰를 할 수 없습니다.")
                ),
                () -> verify(notificationApiClient, never()).openDirectMessageChannel(any(), any()),
                () -> verify(notificationApiClient, never()).sendMessage(any(), any(), any())
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/notification/project_member_t1.sql",
            "classpath:sql/fixtures/notification/notification_settings_t1_u1_dm_enabled.sql"
    })
    void 같은_리뷰어가_같은_PR에서_리뷰_바로_시작을_다시_누르면_이미_시작_안내를_보낸다() {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1")).willReturn("D_AUTHOR");
        BlockActionCommandDto command = commandWithMeta(metaJson(), "U9");

        // when
        startReviewActionHandler.handle(command);
        BlockActionOutcomeDto second = startReviewActionHandler.handle(command);

        // then
        assertAll(
                () -> assertThat(second).isEqualTo(BlockActionOutcomeDto.empty()),
                () -> verify(notificationApiClient, times(1)).openDirectMessageChannel("xoxb-test-token", "U1"),
                () -> verify(notificationApiClient, times(1)).sendMessage(
                        eq("xoxb-test-token"),
                        eq("D_AUTHOR"),
                        argThat(message -> message.contains("<@U1>") && message.contains("<@U9>"))
                ),
                () -> verify(notificationApiClient).sendEphemeralMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        eq("U9"),
                        eq("이미 해당 PR에 대한 리뷰를 시작했습니다.")
                )
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/notification/project_member_t1.sql",
            "classpath:sql/fixtures/notification/notification_settings_t1_u2_dm_disabled.sql"
    })
    void 리뷰이가_DM_비활성_설정이면_리뷰_바로_시작시_리뷰이에게_DM을_보내지_않는다() {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U2")).willReturn("D_REVIEWEE");
        BlockActionCommandDto command = commandWithMeta(metaJsonWithAuthor("U2"), "U8");

        // when
        BlockActionOutcomeDto actual = startReviewActionHandler.handle(command);

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo(BlockActionOutcomeDto.empty()),
                () -> verify(notificationApiClient, never()).openDirectMessageChannel("xoxb-test-token", "U2"),
                () -> verify(notificationApiClient, never()).sendMessage(
                        eq("xoxb-test-token"),
                        eq("D_REVIEWEE"),
                        any()
                ),
                () -> verify(notificationApiClient).sendEphemeralMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        eq("U8"),
                        eq("리뷰 시작 알림을 전송했습니다.")
                )
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/notification/workspace_t1.sql")
    void 리뷰이_DM_설정이_없으면_기본값으로_리뷰_바로_시작시_리뷰이에게_DM을_보낸다() {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U3")).willReturn("D_REVIEWEE");
        BlockActionCommandDto command = commandWithMeta(metaJsonWithAuthor("U3"), "U7");

        // when
        BlockActionOutcomeDto actual = startReviewActionHandler.handle(command);

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo(BlockActionOutcomeDto.empty()),
                () -> verify(notificationApiClient).openDirectMessageChannel("xoxb-test-token", "U3"),
                () -> verify(notificationApiClient).sendMessage(
                        eq("xoxb-test-token"),
                        eq("D_REVIEWEE"),
                        argThat(message -> message.contains("<@U3>") && message.contains("<@U7>"))
                ),
                () -> verify(notificationApiClient).sendEphemeralMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        eq("U7"),
                        eq("리뷰 시작 알림을 전송했습니다.")
                )
        );
    }

    private BlockActionCommandDto commandWithMeta(String metaJson, String reviewerSlackId) {
        JsonNode action = objectMapper.createObjectNode().put("value", metaJson);

        return new BlockActionCommandDto(
                objectMapper.createObjectNode(),
                action,
                BlockActionType.START_REVIEW.value(),
                BlockActionType.START_REVIEW,
                "T1",
                "C1",
                reviewerSlackId,
                "xoxb-test-token"
        );
    }

    private String metaJson() {
        return metaJsonWithAuthor("U1");
    }

    private String metaJsonWithAuthor(String authorSlackId) {
        return objectMapper.createObjectNode()
                .put("team_id", "T1")
                .put("channel_id", "C1")
                .put("pull_request_id", 10L)
                .put("pull_request_number", 10)
                .put("pull_request_title", "PR 제목")
                .put("pull_request_url", "https://github.com/org/repo/pull/10")
                .put("project_id", "123")
                .put("author_github_id", "author-gh")
                .put("author_slack_id", authorSlackId)
                .put("reservation_id", "R1")
                .toString();
    }
}
