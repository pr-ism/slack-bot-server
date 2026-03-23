package com.slack.bot.application.review.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.slack.bot.application.review.ReviewBlockCreator;
import com.slack.bot.application.review.dto.ReviewMessageDto;
import com.slack.bot.application.review.dto.ReviewNotificationPayload;
import com.slack.bot.application.review.meta.ReviewActionMetaBuilder;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutbox;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewNotificationMessageRendererTest {

    @Mock
    ReviewBlockCreator reviewBlockCreator;

    @Mock
    ReviewActionMetaBuilder reviewActionMetaBuilder;

    @Test
    void semantic_payload를_읽어_action_meta와_메시지를_조립한다() throws Exception {
        // given
        ObjectMapper objectMapper = new ObjectMapper();
        ReviewNotificationMessageRenderer renderer = new ReviewNotificationMessageRenderer(
                objectMapper,
                reviewBlockCreator,
                reviewActionMetaBuilder
        );
        ReviewNotificationPayload payload = new ReviewNotificationPayload(
                "repo",
                101L,
                42,
                "Fix bug",
                "https://github.com/pr/1",
                "author-gh",
                List.of("reviewer-gh-1"),
                List.of("reviewer-gh-1")
        );
        ReviewNotificationOutbox outbox = ReviewNotificationOutbox.builder()
                                                                  .idempotencyKey("idempotency")
                                                                  .projectId(1L)
                                                                  .teamId("T1")
                                                                  .channelId("C1")
                                                                  .payloadJson(objectMapper.writeValueAsString(payload))
                                                                  .build();
        ArrayNode blocks = objectMapper.createArrayNode();
        ArrayNode attachments = objectMapper.createArrayNode();
        ReviewMessageDto expected = new ReviewMessageDto(blocks, attachments, "fallback");
        when(reviewActionMetaBuilder.build("T1", "C1", 1L, payload)).thenReturn("{\"meta\":true}");
        when(reviewBlockCreator.create("T1", payload, "{\"meta\":true}")).thenReturn(expected);

        // when
        ReviewMessageDto actual = renderer.render(outbox);

        // then
        assertAll(
                () -> assertThat(actual).isSameAs(expected),
                () -> verify(reviewActionMetaBuilder).build("T1", "C1", 1L, payload),
                () -> verify(reviewBlockCreator).create("T1", payload, "{\"meta\":true}")
        );
    }
}
