package com.slack.bot.infrastructure.interaction.box.in;

import com.slack.bot.domain.common.BaseTimeEntity;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxFailureState;
import com.slack.bot.infrastructure.common.BoxHistoryFailureDetail;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "slack_interaction_inbox_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SlackInteractionInboxHistory extends BaseTimeEntity {

    private Long inboxId;

    private int processingAttempt;

    @Enumerated(EnumType.STRING)
    private SlackInteractionInboxStatus status;

    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    private BoxFailureState failureState;

    @Getter(AccessLevel.NONE)
    private List<BoxHistoryFailureDetail> failureDetails = new ArrayList<>();

    public static SlackInteractionInboxHistory completed(
            Long inboxId,
            int processingAttempt,
            SlackInteractionInboxStatus status,
            Instant completedAt,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        validateInboxIdIfPresent(inboxId);
        validateProcessingAttempt(processingAttempt);
        validateStatus(status);
        validateCompletedAt(completedAt);
        validateFailure(status, failure);

        return new SlackInteractionInboxHistory(
                inboxId,
                processingAttempt,
                status,
                completedAt,
                failure
        );
    }

    public SlackInteractionInboxHistory bindInboxId(Long inboxId) {
        validateInboxId(inboxId);
        if (this.inboxId != null && !this.inboxId.equals(inboxId)) {
            throw new IllegalStateException("history inboxId를 다른 값으로 변경할 수 없습니다.");
        }
        if (this.inboxId != null) {
            return this;
        }

        return new SlackInteractionInboxHistory(
                inboxId,
                processingAttempt,
                status,
                completedAt,
                getFailure()
        );
    }

    private SlackInteractionInboxHistory(
            Long inboxId,
            int processingAttempt,
            SlackInteractionInboxStatus status,
            Instant completedAt,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        this.inboxId = inboxId;
        this.processingAttempt = processingAttempt;
        this.status = status;
        this.completedAt = completedAt;
        applyFailure(failure);
    }

    public BoxFailureSnapshot<SlackInteractionFailureType> getFailure() {
        if (failureState == BoxFailureState.ABSENT) {
            return BoxFailureSnapshot.absent();
        }

        BoxHistoryFailureDetail failureDetail = requireFailureDetail();

        return BoxFailureSnapshot.present(
                failureDetail.getFailureReason(),
                SlackInteractionFailureType.valueOf(failureDetail.getFailureTypeName())
        );
    }

    private static void validateInboxId(Long inboxId) {
        if (inboxId == null || inboxId <= 0) {
            throw new IllegalArgumentException("inboxId는 비어 있을 수 없습니다.");
        }
    }

    private static void validateInboxIdIfPresent(Long inboxId) {
        if (inboxId == null) {
            return;
        }

        validateInboxId(inboxId);
    }

    private static void validateProcessingAttempt(int processingAttempt) {
        if (processingAttempt <= 0) {
            throw new IllegalArgumentException("processingAttempt는 1 이상이어야 합니다.");
        }
    }

    private static void validateStatus(SlackInteractionInboxStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status는 비어 있을 수 없습니다.");
        }
        if (status == SlackInteractionInboxStatus.PENDING || status == SlackInteractionInboxStatus.PROCESSING) {
            throw new IllegalArgumentException("history status는 완료된 상태여야 합니다.");
        }
    }

    private static void validateCompletedAt(Instant completedAt) {
        if (completedAt == null) {
            throw new IllegalArgumentException("completedAt은 비어 있을 수 없습니다.");
        }
    }

    private static void validateFailure(
            SlackInteractionInboxStatus status,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        if (status == SlackInteractionInboxStatus.PROCESSED) {
            if (failure.isPresent()) {
                throw new IllegalArgumentException("PROCESSED history에는 실패 정보가 없어야 합니다.");
            }
            return;
        }

        if (!failure.isPresent()) {
            throw new IllegalArgumentException("완료 실패 정보는 비어 있을 수 없습니다.");
        }

        SlackInteractionFailureType failureType = failure.type();
        if (failureType == SlackInteractionFailureType.ABSENT || failureType == SlackInteractionFailureType.NONE) {
            throw new IllegalArgumentException("완료 실패 정보의 failureType이 올바르지 않습니다.");
        }
        if (status == SlackInteractionInboxStatus.RETRY_PENDING
                && failureType != SlackInteractionFailureType.RETRYABLE
                && failureType != SlackInteractionFailureType.PROCESSING_TIMEOUT) {
            throw new IllegalArgumentException("RETRY_PENDING history의 failureType이 올바르지 않습니다.");
        }
    }

    private void applyFailure(BoxFailureSnapshot<SlackInteractionFailureType> failure) {
        if (!failure.isPresent()) {
            this.failureState = BoxFailureState.ABSENT;
            this.failureDetails.clear();
            return;
        }

        this.failureState = BoxFailureState.PRESENT;
        this.failureDetails.clear();
        this.failureDetails.add(BoxHistoryFailureDetail.of(failure.reason(), failure.type()));
    }

    private BoxHistoryFailureDetail requireFailureDetail() {
        if (failureDetails.size() == 1) {
            return failureDetails.getFirst();
        }

        throw new IllegalStateException("history failure detail 상태가 올바르지 않습니다.");
    }
}
