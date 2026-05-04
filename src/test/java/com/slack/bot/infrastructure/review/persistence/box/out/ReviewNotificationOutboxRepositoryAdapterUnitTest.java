package com.slack.bot.infrastructure.review.persistence.box.out;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutbox;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxStringField;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewNotificationOutboxRepositoryAdapterUnitTest {

    @Test
    void save는_insert_결과가_0건이면_저장_실패_예외를_던진다() {
        // given
        ReviewNotificationOutboxMybatisMapper outboxMybatisMapper = mock(ReviewNotificationOutboxMybatisMapper.class);
        when(outboxMybatisMapper.insert(any())).thenReturn(0);
        ReviewNotificationOutboxRepositoryAdapter adapter = adapter(outboxMybatisMapper);

        // when & then
        assertThatThrownBy(() -> adapter.save(pendingOutbox()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("outbox 저장에 실패했습니다.");
    }

    @Test
    void save는_insert후_generated_key가_비어_있으면_저장_실패_예외를_던진다() {
        // given
        ReviewNotificationOutboxMybatisMapper outboxMybatisMapper = mock(ReviewNotificationOutboxMybatisMapper.class);
        when(outboxMybatisMapper.insert(any())).thenReturn(1);
        ReviewNotificationOutboxRepositoryAdapter adapter = adapter(outboxMybatisMapper);

        // when & then
        assertThatThrownBy(() -> adapter.save(pendingOutbox()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("outbox 저장에 실패했습니다.");
    }

    private ReviewNotificationOutboxRepositoryAdapter adapter(
            ReviewNotificationOutboxMybatisMapper outboxMybatisMapper
    ) {
        NamedParameterJdbcTemplate namedParameterJdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ReviewNotificationOutboxHistoryMybatisMapper historyMybatisMapper =
                mock(ReviewNotificationOutboxHistoryMybatisMapper.class);

        return new ReviewNotificationOutboxRepositoryAdapter(
                namedParameterJdbcTemplate,
                outboxMybatisMapper,
                historyMybatisMapper
        );
    }

    private ReviewNotificationOutbox pendingOutbox() {
        return ReviewNotificationOutbox.channelBlocks(
                "review-outbox-insert-failure",
                "T1",
                "C1",
                "[]",
                ReviewNotificationOutboxStringField.absent(),
                ReviewNotificationOutboxStringField.present("fallback")
        );
    }
}
