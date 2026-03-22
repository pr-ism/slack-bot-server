package com.slack.bot.application.review.box.aop.aspect;

import com.fasterxml.jackson.databind.JsonNode;
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

        reviewNotificationOutboxEnqueuer.enqueueChannelBlocks(
                sourceKey,
                teamId,
                channelId,
                blocks,
                attachments,
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
}
