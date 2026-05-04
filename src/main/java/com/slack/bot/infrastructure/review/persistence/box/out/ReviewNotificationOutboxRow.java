package com.slack.bot.infrastructure.review.persistence.box.out;

import com.slack.bot.infrastructure.common.BoxEventTime;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxProcessingLease;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutbox;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxFieldState;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxMessageType;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxProjectId;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxStatus;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxStringField;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
public class ReviewNotificationOutboxRow {

    private Long id;
    private ReviewNotificationOutboxMessageType messageType;
    private String idempotencyKey;
    private Long projectId;
    private String teamId;
    private String channelId;
    private ReviewNotificationOutboxFieldState payloadJsonState;
    private String payloadJson;
    private ReviewNotificationOutboxFieldState blocksJsonState;
    private String blocksJson;
    private ReviewNotificationOutboxFieldState attachmentsJsonState;
    private String attachmentsJson;
    private ReviewNotificationOutboxFieldState fallbackTextState;
    private String fallbackText;
    private ReviewNotificationOutboxStatus status;
    private int processingAttempt;
    private Instant processingStartedAt;
    private Instant sentAt;
    private Instant failedAt;
    private String failureReason;
    private SlackInteractionFailureType failureType;

    @Builder
    public ReviewNotificationOutboxRow(
            Long id,
            ReviewNotificationOutboxMessageType messageType,
            String idempotencyKey,
            Long projectId,
            String teamId,
            String channelId,
            ReviewNotificationOutboxFieldState payloadJsonState,
            String payloadJson,
            ReviewNotificationOutboxFieldState blocksJsonState,
            String blocksJson,
            ReviewNotificationOutboxFieldState attachmentsJsonState,
            String attachmentsJson,
            ReviewNotificationOutboxFieldState fallbackTextState,
            String fallbackText,
            ReviewNotificationOutboxStatus status,
            int processingAttempt,
            Instant processingStartedAt,
            Instant sentAt,
            Instant failedAt,
            String failureReason,
            SlackInteractionFailureType failureType
    ) {
        this.id = id;
        this.messageType = messageType;
        this.idempotencyKey = idempotencyKey;
        this.projectId = projectId;
        this.teamId = teamId;
        this.channelId = channelId;
        this.payloadJsonState = payloadJsonState;
        this.payloadJson = payloadJson;
        this.blocksJsonState = blocksJsonState;
        this.blocksJson = blocksJson;
        this.attachmentsJsonState = attachmentsJsonState;
        this.attachmentsJson = attachmentsJson;
        this.fallbackTextState = fallbackTextState;
        this.fallbackText = fallbackText;
        this.status = status;
        this.processingAttempt = processingAttempt;
        this.processingStartedAt = processingStartedAt;
        this.sentAt = sentAt;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
        this.failureType = failureType;
    }

    public static ReviewNotificationOutboxRow from(ReviewNotificationOutbox outbox) {
        ReviewNotificationOutboxRowBuilder rowBuilder = ReviewNotificationOutboxRow.builder()
                                                                                   .messageType(outbox.getMessageType())
                                                                                   .idempotencyKey(outbox.getIdempotencyKey())
                                                                                   .teamId(outbox.getTeamId())
                                                                                   .channelId(outbox.getChannelId())
                                                                                   .payloadJsonState(outbox.getPayloadJson().getState())
                                                                                   .payloadJson(outbox.getPayloadJson().valueOrBlank())
                                                                                   .blocksJsonState(outbox.getBlocksJson().getState())
                                                                                   .blocksJson(outbox.getBlocksJson().valueOrBlank())
                                                                                   .attachmentsJsonState(outbox.getAttachmentsJson().getState())
                                                                                   .attachmentsJson(outbox.getAttachmentsJson().valueOrBlank())
                                                                                   .fallbackTextState(outbox.getFallbackText().getState())
                                                                                   .fallbackText(outbox.getFallbackText().valueOrBlank())
                                                                                   .status(outbox.getStatus())
                                                                                   .processingAttempt(outbox.getProcessingAttempt());
        applyProjectId(rowBuilder, outbox);
        applyProcessingStartedAt(rowBuilder, outbox);
        applySentAt(rowBuilder, outbox);
        applyFailedAt(rowBuilder, outbox);
        applyFailure(rowBuilder, outbox);
        if (outbox.hasId()) {
            rowBuilder.id(outbox.getId());
        }

        return rowBuilder.build();
    }

    public ReviewNotificationOutbox toDomain() {
        return ReviewNotificationOutbox.rehydrate(
                id,
                messageType,
                idempotencyKey,
                toProjectId(),
                teamId,
                channelId,
                toStringField(payloadJsonState, payloadJson),
                toStringField(blocksJsonState, blocksJson),
                toStringField(attachmentsJsonState, attachmentsJson),
                toStringField(fallbackTextState, fallbackText),
                status,
                processingAttempt,
                toProcessingLease(),
                toSentTime(),
                toFailedTime(),
                toFailure()
        );
    }

    private static void applyProjectId(
            ReviewNotificationOutboxRowBuilder rowBuilder,
            ReviewNotificationOutbox outbox
    ) {
        if (!outbox.getProjectId().isPresent()) {
            return;
        }

        rowBuilder.projectId(outbox.getProjectId().value());
    }

    private static void applyProcessingStartedAt(
            ReviewNotificationOutboxRowBuilder rowBuilder,
            ReviewNotificationOutbox outbox
    ) {
        if (!outbox.getProcessingLease().isClaimed()) {
            return;
        }

        rowBuilder.processingStartedAt(outbox.getProcessingLease().startedAt());
    }

    private static void applySentAt(
            ReviewNotificationOutboxRowBuilder rowBuilder,
            ReviewNotificationOutbox outbox
    ) {
        if (!outbox.getSentTime().isPresent()) {
            return;
        }

        rowBuilder.sentAt(outbox.getSentTime().occurredAt());
    }

    private static void applyFailedAt(
            ReviewNotificationOutboxRowBuilder rowBuilder,
            ReviewNotificationOutbox outbox
    ) {
        if (!outbox.getFailedTime().isPresent()) {
            return;
        }

        rowBuilder.failedAt(outbox.getFailedTime().occurredAt());
    }

    private static void applyFailure(
            ReviewNotificationOutboxRowBuilder rowBuilder,
            ReviewNotificationOutbox outbox
    ) {
        if (!outbox.getFailure().isPresent()) {
            return;
        }

        rowBuilder.failureReason(outbox.getFailure().reason());
        rowBuilder.failureType(outbox.getFailure().type());
    }

    private ReviewNotificationOutboxProjectId toProjectId() {
        if (projectId == null) {
            return ReviewNotificationOutboxProjectId.absent();
        }

        return ReviewNotificationOutboxProjectId.present(projectId);
    }

    private ReviewNotificationOutboxStringField toStringField(
            ReviewNotificationOutboxFieldState state,
            String value
    ) {
        validateStringField(state, value);
        if (state == ReviewNotificationOutboxFieldState.ABSENT) {
            return ReviewNotificationOutboxStringField.absent();
        }

        return ReviewNotificationOutboxStringField.present(value);
    }

    private BoxProcessingLease toProcessingLease() {
        if (processingStartedAt == null) {
            return BoxProcessingLease.idle();
        }

        return BoxProcessingLease.claimed(processingStartedAt);
    }

    private BoxEventTime toSentTime() {
        if (sentAt == null) {
            return BoxEventTime.absent();
        }

        return BoxEventTime.present(sentAt);
    }

    private BoxEventTime toFailedTime() {
        if (failedAt == null) {
            return BoxEventTime.absent();
        }

        return BoxEventTime.present(failedAt);
    }

    private BoxFailureSnapshot<SlackInteractionFailureType> toFailure() {
        if (failureReason == null && failureType == null) {
            return BoxFailureSnapshot.absent();
        }
        if (failureReason == null || failureType == null) {
            throw new IllegalStateException("failure 상태가 올바르지 않습니다.");
        }

        return BoxFailureSnapshot.present(failureReason, failureType);
    }

    private void validateStringField(
            ReviewNotificationOutboxFieldState state,
            String value
    ) {
        if (state == null) {
            throw new IllegalStateException("payload field state가 비어 있을 수 없습니다.");
        }
        if (value == null) {
            throw new IllegalStateException("payload field value가 비어 있을 수 없습니다.");
        }
    }
}
