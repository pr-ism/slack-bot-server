package com.slack.bot.infrastructure.review.persistence.box.out;

import com.slack.bot.domain.common.BaseTimeEntity;
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
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "review_notification_outbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewNotificationOutboxJpaEntity extends BaseTimeEntity {

    @Enumerated(EnumType.STRING)
    private ReviewNotificationOutboxMessageType messageType;

    private String idempotencyKey;

    private Long projectId;

    private String teamId;

    private String channelId;

    @Enumerated(EnumType.STRING)
    private ReviewNotificationOutboxFieldState payloadJsonState;

    private String payloadJson;

    @Enumerated(EnumType.STRING)
    private ReviewNotificationOutboxFieldState blocksJsonState;

    private String blocksJson;

    @Enumerated(EnumType.STRING)
    private ReviewNotificationOutboxFieldState attachmentsJsonState;

    private String attachmentsJson;

    @Enumerated(EnumType.STRING)
    private ReviewNotificationOutboxFieldState fallbackTextState;

    private String fallbackText;

    @Enumerated(EnumType.STRING)
    private ReviewNotificationOutboxStatus status;

    private int processingAttempt;

    private Instant processingStartedAt;

    private Instant sentAt;

    private Instant failedAt;

    private String failureReason;

    @Enumerated(EnumType.STRING)
    private SlackInteractionFailureType failureType;

    public ReviewNotificationOutbox toDomain() {
        return ReviewNotificationOutbox.rehydrate(
                getId(),
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
                toEventTime(sentAt),
                toEventTime(failedAt),
                toFailure()
        );
    }

    public void apply(ReviewNotificationOutbox outbox) {
        this.messageType = outbox.getMessageType();
        this.idempotencyKey = outbox.getIdempotencyKey();
        applyProjectId(outbox);
        this.teamId = outbox.getTeamId();
        this.channelId = outbox.getChannelId();
        applyStringField(outbox.getPayloadJson(), FieldType.PAYLOAD_JSON);
        applyStringField(outbox.getBlocksJson(), FieldType.BLOCKS_JSON);
        applyStringField(outbox.getAttachmentsJson(), FieldType.ATTACHMENTS_JSON);
        applyStringField(outbox.getFallbackText(), FieldType.FALLBACK_TEXT);
        this.status = outbox.getStatus();
        this.processingAttempt = outbox.getProcessingAttempt();
        applyProcessingLease(outbox.getProcessingLease());
        applyEventTime(outbox.getSentTime(), FieldType.SENT_TIME);
        applyEventTime(outbox.getFailedTime(), FieldType.FAILED_TIME);
        applyFailure(outbox.getFailure());
    }

    private void applyProjectId(ReviewNotificationOutbox outbox) {
        this.projectId = null;
        if (outbox.getProjectId().isPresent()) {
            this.projectId = outbox.getProjectId().value();
        }
    }

    private ReviewNotificationOutboxProjectId toProjectId() {
        if (projectId == null) {
            return ReviewNotificationOutboxProjectId.absent();
        }

        return ReviewNotificationOutboxProjectId.present(projectId);
    }

    private void applyStringField(
            ReviewNotificationOutboxStringField field,
            FieldType fieldType
    ) {
        if (fieldType == FieldType.PAYLOAD_JSON) {
            this.payloadJsonState = field.getState();
            this.payloadJson = field.valueOrBlank();
            return;
        }
        if (fieldType == FieldType.BLOCKS_JSON) {
            this.blocksJsonState = field.getState();
            this.blocksJson = field.valueOrBlank();
            return;
        }
        if (fieldType == FieldType.ATTACHMENTS_JSON) {
            this.attachmentsJsonState = field.getState();
            this.attachmentsJson = field.valueOrBlank();
            return;
        }

        this.fallbackTextState = field.getState();
        this.fallbackText = field.valueOrBlank();
    }

    private void applyProcessingLease(BoxProcessingLease processingLease) {
        this.processingStartedAt = null;
        if (!processingLease.isClaimed()) {
            return;
        }

        this.processingStartedAt = processingLease.startedAt();
    }

    private void applyEventTime(
            BoxEventTime eventTime,
            FieldType fieldType
    ) {
        if (fieldType == FieldType.SENT_TIME) {
            this.sentAt = null;
            if (!eventTime.isPresent()) {
                return;
            }

            this.sentAt = eventTime.occurredAt();
            return;
        }

        this.failedAt = null;
        if (!eventTime.isPresent()) {
            return;
        }

        this.failedAt = eventTime.occurredAt();
    }

    private void applyFailure(BoxFailureSnapshot<SlackInteractionFailureType> failure) {
        this.failureReason = null;
        this.failureType = null;
        if (!failure.isPresent()) {
            return;
        }

        this.failureReason = failure.reason();
        this.failureType = failure.type();
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

    private BoxEventTime toEventTime(Instant occurredAt) {
        if (occurredAt == null) {
            return BoxEventTime.absent();
        }

        return BoxEventTime.present(occurredAt);
    }

    private BoxFailureSnapshot<SlackInteractionFailureType> toFailure() {
        if (failureReason == null && failureType == null) {
            return BoxFailureSnapshot.absent();
        }
        if (failureReason == null || failureType == null) {
            throw new IllegalStateException("outbox failure 상태가 올바르지 않습니다.");
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

    private enum FieldType {
        PAYLOAD_JSON,
        BLOCKS_JSON,
        ATTACHMENTS_JSON,
        FALLBACK_TEXT,
        SENT_TIME,
        FAILED_TIME
    }
}
