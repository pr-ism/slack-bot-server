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
import com.slack.bot.application.interactivity.block.handler.store.StartReviewMarkStore;
import com.slack.bot.application.interactivity.client.NotificationApiClient;
import com.slack.bot.application.interactivity.publisher.ReviewReservationFulfilledEvent;
import com.slack.bot.domain.reservation.ReservationStatus;
import com.slack.bot.domain.reservation.repository.ReviewReservationRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@RecordApplicationEvents
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class StartReviewActionHandlerTest {

    @Autowired
    StartReviewActionHandler startReviewActionHandler;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NotificationApiClient notificationApiClient;

    @Autowired
    ReviewReservationRepository reviewReservationRepository;

    @Autowired
    StartReviewMarkStore startReviewMarkStore;

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/notification/project_member_t1.sql",
            "classpath:sql/fixtures/notification/notification_settings_t1_u1_dm_enabled.sql"
    })
    void 리뷰_바로_시작_액션이면_리뷰이_DM_설정이_켜져있을때_즉시_DM을_보낸다(ApplicationEvents applicationEvents) {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1")).willReturn("D_AUTHOR");
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U2")).willReturn("D_REVIEWER");
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
                () -> verify(notificationApiClient).openDirectMessageChannel("xoxb-test-token", "U2"),
                () -> verify(notificationApiClient).sendMessage(
                        eq("xoxb-test-token"),
                        eq("D_REVIEWER"),
                        argThat(message -> message.contains("<@U1>") && message.contains("<@U2>"))
                ),
                () -> assertThat(applicationEvents.stream(ReviewReservationFulfilledEvent.class).toList())
                        .singleElement()
                        .satisfies(event -> assertAll(
                                () -> assertThat(event.teamId()).isEqualTo("T1"),
                                () -> assertThat(event.projectId()).isEqualTo(123L),
                                () -> assertThat(event.slackUserId()).isEqualTo("U2"),
                                () -> assertThat(event.pullRequestId()).isEqualTo(10L)
                        ))
        );
    }

    @Test
    void 메타가_없으면_리뷰_시작_DM을_보내지_않는다(ApplicationEvents applicationEvents) {
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
                () -> verify(notificationApiClient, never()).sendMessage(any(), any(), any()),
                () -> assertThat(applicationEvents.stream(ReviewReservationFulfilledEvent.class).toList()).isEmpty()
        );
    }

    @Test
    void 메타가_잘못되면_아무_알림도_보내지_않는다(ApplicationEvents applicationEvents) {
        // given
        BlockActionCommandDto command = commandWithMeta("invalid-json", "U2");

        // when
        BlockActionOutcomeDto actual = startReviewActionHandler.handle(command);

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo(BlockActionOutcomeDto.empty()),
                () -> verify(notificationApiClient, never()).sendEphemeralMessage(any(), any(), any(), any()),
                () -> verify(notificationApiClient, never()).openDirectMessageChannel(any(), any()),
                () -> verify(notificationApiClient, never()).sendMessage(any(), any(), any()),
                () -> assertThat(applicationEvents.stream(ReviewReservationFulfilledEvent.class).toList()).isEmpty()
        );
    }

    @Test
    void 리뷰이가_리뷰_바로_시작을_누르면_예약_불가_알림을_보내고_DM을_보내지_않는다(ApplicationEvents applicationEvents) {
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
                () -> verify(notificationApiClient, never()).sendMessage(any(), any(), any()),
                () -> assertThat(applicationEvents.stream(ReviewReservationFulfilledEvent.class).toList()).isEmpty()
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/notification/project_member_t1.sql",
            "classpath:sql/fixtures/notification/notification_settings_t1_u1_dm_enabled.sql"
    })
    void 요청자_슬랙_ID가_비어있으면_리뷰이_자기예약_검증을_건너뛰고_알림을_보낸다(ApplicationEvents applicationEvents) {
        // given
        BlockActionCommandDto command = commandWithMeta(metaJson(), "");

        // when
        BlockActionOutcomeDto actual = startReviewActionHandler.handle(command);

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo(BlockActionOutcomeDto.empty()),
                () -> verify(notificationApiClient, never()).openDirectMessageChannel(any(), any()),
                () -> verify(notificationApiClient, never()).sendMessage(any(), any(), any()),
                () -> verify(notificationApiClient).sendEphemeralMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        eq(""),
                        eq("리뷰 시작 알림을 전송했습니다.")
                ),
                () -> assertThat(applicationEvents.stream(ReviewReservationFulfilledEvent.class).toList()).isEmpty()
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/notification/project_member_t1.sql",
            "classpath:sql/fixtures/notification/notification_settings_t1_u1_dm_enabled.sql"
    })
    void 같은_리뷰어가_같은_PR에서_리뷰_바로_시작을_다시_누르면_이미_시작_안내를_보낸다(ApplicationEvents applicationEvents) {
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
                ),
                () -> assertThat(applicationEvents.stream(ReviewReservationFulfilledEvent.class).toList())
                        .singleElement()
                        .satisfies(event -> assertAll(
                                () -> assertThat(event.teamId()).isEqualTo("T1"),
                                () -> assertThat(event.projectId()).isEqualTo(123L),
                                () -> assertThat(event.slackUserId()).isEqualTo("U9"),
                                () -> assertThat(event.pullRequestId()).isEqualTo(10L)
                        ))
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/notification/project_member_t1.sql",
            "classpath:sql/fixtures/notification/notification_settings_t1_u1_dm_enabled.sql"
    })
    void 시작_마크가_TTL_만료면_다시_리뷰_시작_알림을_보낸다(ApplicationEvents applicationEvents) {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1")).willReturn("D_AUTHOR");
        startReviewMarkStore.put("T1:123:10:U9", Instant.now().minusSeconds(8L * 24 * 60 * 60));
        BlockActionCommandDto command = commandWithMeta(metaJson(), "U9");

        // when
        BlockActionOutcomeDto actual = startReviewActionHandler.handle(command);

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo(BlockActionOutcomeDto.empty()),
                () -> verify(notificationApiClient).openDirectMessageChannel("xoxb-test-token", "U1"),
                () -> verify(notificationApiClient).sendMessage(
                        eq("xoxb-test-token"),
                        eq("D_AUTHOR"),
                        argThat(message -> message.contains("<@U1>") && message.contains("<@U9>"))
                ),
                () -> verify(notificationApiClient).sendEphemeralMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        eq("U9"),
                        eq("리뷰 시작 알림을 전송했습니다.")
                ),
                () -> assertThat(applicationEvents.stream(ReviewReservationFulfilledEvent.class).toList())
                        .singleElement()
                        .satisfies(event -> assertAll(
                                () -> assertThat(event.teamId()).isEqualTo("T1"),
                                () -> assertThat(event.projectId()).isEqualTo(123L),
                                () -> assertThat(event.slackUserId()).isEqualTo("U9"),
                                () -> assertThat(event.pullRequestId()).isEqualTo(10L)
                        ))
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/notification/project_member_t1.sql",
            "classpath:sql/fixtures/notification/notification_settings_t1_u2_dm_disabled.sql"
    })
    void 리뷰이가_DM_비활성_설정이면_리뷰_바로_시작시_리뷰이에게_DM을_보내지_않는다(ApplicationEvents applicationEvents) {
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
                ),
                () -> assertThat(applicationEvents.stream(ReviewReservationFulfilledEvent.class).toList())
                        .singleElement()
                        .satisfies(event -> assertAll(
                                () -> assertThat(event.teamId()).isEqualTo("T1"),
                                () -> assertThat(event.projectId()).isEqualTo(123L),
                                () -> assertThat(event.slackUserId()).isEqualTo("U8"),
                                () -> assertThat(event.pullRequestId()).isEqualTo(10L)
                        ))
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/notification/workspace_t1.sql")
    void 리뷰이_DM_설정이_없으면_기본값으로_리뷰_바로_시작시_리뷰이에게_DM을_보낸다(ApplicationEvents applicationEvents) {
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
                ),
                () -> assertThat(applicationEvents.stream(ReviewReservationFulfilledEvent.class).toList())
                        .singleElement()
                        .satisfies(event -> assertAll(
                                () -> assertThat(event.teamId()).isEqualTo("T1"),
                                () -> assertThat(event.projectId()).isEqualTo(123L),
                                () -> assertThat(event.slackUserId()).isEqualTo("U7"),
                                () -> assertThat(event.pullRequestId()).isEqualTo(10L)
                        ))
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/notification/project_member_t1.sql",
            "classpath:sql/fixtures/notification/notification_settings_t1_u1_dm_enabled.sql"
    })
    void 리뷰어_DM_설정이_켜져있으면_리뷰어에게도_지금_시작_알림_DM을_보낸다(ApplicationEvents applicationEvents) {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U2")).willReturn("D_REVIEWEE");
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1")).willReturn("D_REVIEWER");
        BlockActionCommandDto command = commandWithMeta(metaJsonWithAuthorAndPullRequest("U2", 11L, 11), "U1");

        // when
        BlockActionOutcomeDto actual = startReviewActionHandler.handle(command);

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo(BlockActionOutcomeDto.empty()),
                () -> verify(notificationApiClient).openDirectMessageChannel("xoxb-test-token", "U2"),
                () -> verify(notificationApiClient).openDirectMessageChannel("xoxb-test-token", "U1"),
                () -> verify(notificationApiClient, times(2)).sendMessage(
                        eq("xoxb-test-token"),
                        any(),
                        argThat(message -> message.contains("<@U2>") && message.contains("<@U1>"))
                ),
                () -> assertThat(applicationEvents.stream(ReviewReservationFulfilledEvent.class).toList())
                        .singleElement()
                        .satisfies(event -> assertAll(
                                () -> assertThat(event.teamId()).isEqualTo("T1"),
                                () -> assertThat(event.projectId()).isEqualTo(123L),
                                () -> assertThat(event.slackUserId()).isEqualTo("U1"),
                                () -> assertThat(event.pullRequestId()).isEqualTo(11L)
                        ))
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql")
    void 리뷰_바로_시작을_누르면_해당_활성_예약을_즉시_종료한다(ApplicationEvents applicationEvents) {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U_AUTHOR")).willReturn("D_AUTHOR");
        BlockActionCommandDto command = commandWithMeta(metaJsonWithAuthor("U_AUTHOR"), "U1");

        // when
        BlockActionOutcomeDto actual = startReviewActionHandler.handle(command);

        // then
        Optional<com.slack.bot.domain.reservation.ReviewReservation> reservation = reviewReservationRepository.findById(100L);

        assertAll(
                () -> assertThat(actual).isEqualTo(BlockActionOutcomeDto.empty()),
                () -> assertThat(reservation).isPresent(),
                () -> assertThat(reservation.get().getStatus()).isEqualTo(ReservationStatus.CANCELLED),
                () -> assertThat(applicationEvents.stream(ReviewReservationFulfilledEvent.class).toList())
                        .singleElement()
                        .satisfies(event -> assertAll(
                                () -> assertThat(event.teamId()).isEqualTo("T1"),
                                () -> assertThat(event.projectId()).isEqualTo(123L),
                                () -> assertThat(event.slackUserId()).isEqualTo("U1"),
                                () -> assertThat(event.pullRequestId()).isEqualTo(10L)
                        ))
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/interactivity/workspace_t1.sql",
            "classpath:sql/fixtures/interactivity/project_member_t1.sql",
            "classpath:sql/fixtures/interactivity/notification_settings_t1_u1_dm_enabled.sql"
    })
    void 프로젝트_ID가_숫자가_아니면_예약_취소는_건너뛰고_알림은_보낸다(ApplicationEvents applicationEvents) {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1")).willReturn("D_AUTHOR");
        BlockActionCommandDto command = commandWithMeta(metaJsonWithAuthorAndProjectId("U1", "project-x"), "U9");

        // when
        BlockActionOutcomeDto actual = startReviewActionHandler.handle(command);

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo(BlockActionOutcomeDto.empty()),
                () -> verify(notificationApiClient).openDirectMessageChannel("xoxb-test-token", "U1"),
                () -> verify(notificationApiClient).sendMessage(
                        eq("xoxb-test-token"),
                        eq("D_AUTHOR"),
                        argThat(message -> message.contains("<@U1>") && message.contains("<@U9>"))
                ),
                () -> verify(notificationApiClient).sendEphemeralMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        eq("U9"),
                        eq("리뷰 시작 알림을 전송했습니다.")
                ),
                () -> assertThat(applicationEvents.stream(ReviewReservationFulfilledEvent.class).toList()).isEmpty()
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
        return metaJsonWithAuthorAndProjectId(authorSlackId, "123");
    }

    private String metaJsonWithAuthorAndProjectId(String authorSlackId, String projectId) {
        return metaJsonWithAuthorPullRequestAndProjectId(authorSlackId, 10L, 10, projectId);
    }

    private String metaJsonWithAuthorAndPullRequest(String authorSlackId, Long pullRequestId, int pullRequestNumber) {
        return metaJsonWithAuthorPullRequestAndProjectId(authorSlackId, pullRequestId, pullRequestNumber, "123");
    }

    private String metaJsonWithAuthorPullRequestAndProjectId(
            String authorSlackId,
            Long pullRequestId,
            int pullRequestNumber,
            String projectId
    ) {
        return objectMapper.createObjectNode()
                           .put("team_id", "T1")
                           .put("channel_id", "C1")
                           .put("pull_request_id", pullRequestId)
                           .put("pull_request_number", pullRequestNumber)
                           .put("pull_request_title", "PR 제목 " + pullRequestNumber)
                           .put("pull_request_url", "https://github.com/org/repo/pull/" + pullRequestNumber)
                           .put("project_id", projectId)
                           .put("author_github_id", "author-gh")
                           .put("author_slack_id", authorSlackId)
                           .put("reservation_id", "R1")
                           .toString();
    }
}
