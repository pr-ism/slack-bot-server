package com.slack.bot.application.review.box.aop.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.slack.bot.application.review.box.ReviewNotificationSourceContext;
import com.slack.bot.application.review.box.out.ReviewNotificationOutboxEnqueuer;
import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class ReviewNotificationOutboxEnqueueAspect {

    private final ObjectMapper objectMapper;
    private final WorkspaceRepository workspaceRepository;
    private final ReviewNotificationOutboxEnqueuer reviewNotificationOutboxEnqueuer;
    private final ReviewNotificationSourceContext reviewNotificationSourceContext;

    @Around(
            "@annotation(com.slack.bot.application.review.box.aop.EnqueueReviewNotificationOutbox)"
                    + " && args(token, channelId, blocks, attachments, fallbackText)"
    )
    public Object enqueue(
            ProceedingJoinPoint joinPoint,
            String token,
            String channelId,
            JsonNode blocks,
            JsonNode attachments,
            String fallbackText
    ) {
        String sourceKey = reviewNotificationSourceContext.requireSourceKey();
        String teamId = resolveTeamId(token);
        JsonNode mergedBlocks = mergeBlocks(blocks, attachments);

        reviewNotificationOutboxEnqueuer.enqueueChannelBlocks(
                sourceKey,
                teamId,
                channelId,
                mergedBlocks,
                fallbackText
        );

        return null;
    }

    private String resolveTeamId(String token) {
        Workspace workspace = workspaceRepository.findByAccessToken(token)
                                                 .orElseThrow(() -> new IllegalStateException(
                                                         "토큰에 해당하는 워크스페이스를 찾을 수 없습니다."
                                                 ));

        return workspace.getTeamId();
    }

    private JsonNode mergeBlocks(JsonNode topBlocks, JsonNode attachments) {
        ArrayNode merged = objectMapper.createArrayNode();

        appendBlocks(merged, topBlocks);
        appendAttachmentBlocks(merged, attachments);

        return merged;
    }

    private void appendBlocks(ArrayNode merged, JsonNode blocks) {
        if (blocks == null || !blocks.isArray()) {
            return;
        }

        for (JsonNode block : blocks) {
            merged.add(block);
        }
    }

    private void appendAttachmentBlocks(ArrayNode merged, JsonNode attachments) {
        if (attachments == null || !attachments.isArray()) {
            return;
        }

        for (JsonNode attachment : attachments) {
            appendBlocks(merged, attachment.path("blocks"));
        }
    }
}
