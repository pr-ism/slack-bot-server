package com.slack.bot.application.review.box.aop.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.review.box.ReviewNotificationSourceContext;
import com.slack.bot.application.review.box.aop.aspect.support.ReviewAspectIntegrationProbes.ReviewNotificationOutboxEnqueueProbe;
import com.slack.bot.application.review.box.out.ReviewNotificationOutboxEnqueuer;
import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.mockito.ArgumentCaptor;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewNotificationOutboxEnqueueAspectIntegrationTest {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    WorkspaceRepository workspaceRepository;

    @Autowired
    ReviewNotificationOutboxEnqueuer reviewNotificationOutboxEnqueuer;

    @Autowired
    ReviewNotificationSourceContext reviewNotificationSourceContext;

    @Autowired
    ReviewNotificationOutboxEnqueueProbe actualReviewNotificationOutboxEnqueueProbe;

    @BeforeEach
    void setUp() {
        actualReviewNotificationOutboxEnqueueProbe.reset();
    }

    @Test
    void source_key가_있으면_대상_메서드_실행_대신_outbox에_적재한다() {
        // given
        String token = "xoxb-token-" + System.nanoTime();
        String teamId = "T-" + System.nanoTime();
        String channelId = "C123";
        String fallbackText = "fallback text";
        String sourceKey = "REVIEW_REQUEST:api-key:101";
        workspaceRepository.save(Workspace.builder()
                                          .teamId(teamId)
                                          .accessToken(token)
                                          .botUserId("U_BOT")
                                          .userId(1L)
                                          .build());
        JsonNode blocks = topLevelBlocks();
        JsonNode attachments = attachmentBlocks();

        // when
        Object actual = reviewNotificationSourceContext.withSourceKey(
                sourceKey,
                () -> actualReviewNotificationOutboxEnqueueProbe.send(
                        token, channelId, blocks, attachments, fallbackText
                )
        );
        ArgumentCaptor<JsonNode> blocksCaptor = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<JsonNode> attachmentsCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(reviewNotificationOutboxEnqueuer).enqueueChannelBlocks(
                eq(sourceKey),
                eq(teamId),
                eq(channelId),
                blocksCaptor.capture(),
                attachmentsCaptor.capture(),
                eq(fallbackText)
        );

        // then
        JsonNode actualBlocks = blocksCaptor.getValue();
        JsonNode actualAttachments = attachmentsCaptor.getValue();

        assertAll(
                () -> assertThat(actual).isNull(),
                () -> assertThat(actualReviewNotificationOutboxEnqueueProbe.proceedCount()).isZero(),
                () -> assertThat(actualBlocks).isEqualTo(blocks),
                () -> assertThat(actualAttachments).isEqualTo(attachments)
        );
    }

    @Test
    void source_key가_없으면_예외를_던지고_outbox에_적재하지_않는다() {
        // given
        String token = "xoxb-token-" + System.nanoTime();
        String teamId = "T-" + System.nanoTime();
        workspaceRepository.save(Workspace.builder()
                                          .teamId(teamId)
                                          .accessToken(token)
                                          .botUserId("U_BOT")
                                          .userId(1L)
                                          .build());

        // when & then
        assertThatThrownBy(() -> actualReviewNotificationOutboxEnqueueProbe.send(
                token,
                "C123",
                topLevelBlocks(),
                attachmentBlocks(),
                "fallback text"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sourceKey가 필요합니다");

        int actual = actualReviewNotificationOutboxEnqueueProbe.proceedCount();
        assertThat(actual).isZero();
        verify(reviewNotificationOutboxEnqueuer, never()).enqueueChannelBlocks(
                        anyString(),
                        anyString(),
                        anyString(),
                        any(JsonNode.class),
                        any(JsonNode.class),
                        any()
                );
    }

    private JsonNode topLevelBlocks() {
        ArrayNode blocks = objectMapper.createArrayNode();
        blocks.add(objectMapper.createObjectNode().put("type", "section"));
        return blocks;
    }

    private JsonNode attachmentBlocks() {
        ArrayNode attachments = objectMapper.createArrayNode();

        ObjectNode firstAttachment = objectMapper.createObjectNode();
        ArrayNode firstBlocks = objectMapper.createArrayNode();
        firstBlocks.add(objectMapper.createObjectNode().put("type", "actions"));
        firstBlocks.add(objectMapper.createObjectNode().put("type", "context"));
        firstAttachment.set("blocks", firstBlocks);
        attachments.add(firstAttachment);

        attachments.add(objectMapper.createObjectNode());
        return attachments;
    }
}
