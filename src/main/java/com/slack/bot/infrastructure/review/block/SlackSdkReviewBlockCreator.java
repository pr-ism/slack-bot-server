package com.slack.bot.infrastructure.review.block;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.api.model.Attachment;
import com.slack.api.model.block.ActionsBlock;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import com.slack.api.model.block.composition.PlainTextObject;
import com.slack.api.model.block.composition.TextObject;
import com.slack.api.model.block.element.BlockElement;
import com.slack.api.model.block.element.ButtonElement;
import com.slack.api.util.json.GsonFactory;
import com.slack.bot.application.review.ReviewBlockCreator;
import com.slack.bot.application.review.dto.ReviewMessageDto;
import com.slack.bot.application.review.participant.ReviewParticipantFormatter;
import com.slack.bot.application.review.dto.request.ReviewAssignmentRequest;
import com.slack.bot.application.review.participant.dto.ReviewParticipantsDto;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SlackSdkReviewBlockCreator implements ReviewBlockCreator {

    private static final String SCHEDULE_ACTION_ID = "open_review_scheduler";
    private static final String START_REVIEW_ACTION_ID = "start_review";
    private static final String CLAIM_ACTION_PREFIX = "claim_github_id_";
    private static final String ATTACHMENT_COLOR = "#6366F1";

    private final ObjectMapper objectMapper;
    private final ReviewParticipantFormatter mentionFormatter;

    @Override
    public ReviewMessageDto create(String teamId, ReviewAssignmentRequest event, String actionMetaJson) {
        ReviewParticipantsDto participants = mentionFormatter.format(teamId, event);
        List<LayoutBlock> topBlocks = buildTopBlocks(event);
        Attachment attachment = buildAttachment(event, actionMetaJson, participants);
        String fallbackText = buildFallbackText(event);

        return new ReviewMessageDto(
                toJsonNode(topBlocks),
                toJsonNode(List.of(attachment)),
                fallbackText
        );
    }

    private SectionBlock buildTitleBlock(ReviewAssignmentRequest event) {
        return SectionBlock.builder()
                           .text(MarkdownTextObject.builder()
                                                   .text(formatTitleMarkdown(event))
                                                   .build())
                           .build();
    }

    private String formatTitleMarkdown(ReviewAssignmentRequest event) {
        return "🚀 *New PR:* <%s|%s (#%d)>".formatted(
                event.pullRequestUrl(),
                event.pullRequestTitle(),
                event.pullRequestNumber()
        );
    }

    private List<LayoutBlock> buildTopBlocks(ReviewAssignmentRequest event) {
        List<LayoutBlock> blocks = new ArrayList<>();

        blocks.add(buildTitleBlock(event));
        return blocks;
    }

    private Attachment buildAttachment(
            ReviewAssignmentRequest event,
            String actionMetaJson,
            ReviewParticipantsDto participants
    ) {
        List<LayoutBlock> attachmentBlocks = new ArrayList<>();

        attachmentBlocks.add(buildIntroBlock());
        attachmentBlocks.add(buildDetailsBlock(participants));
        attachmentBlocks.add(buildActionButtons(event, actionMetaJson));

        if (!participants.unmappedGithubIds().isEmpty()) {
            attachmentBlocks.add(buildClaimButtons(participants.unmappedGithubIds()));
        }

        return Attachment.builder()
                .color(ATTACHMENT_COLOR)
                .blocks(attachmentBlocks)
                .build();
    }

    private SectionBlock buildIntroBlock() {
        return SectionBlock.builder()
                .text(MarkdownTextObject.builder()
                        .text("새로운 PR이 도착했습니다!")
                        .build())
                .build();
    }

    private SectionBlock buildDetailsBlock(ReviewParticipantsDto participants) {
        List<TextObject> fields = new ArrayList<>();

        fields.add(MarkdownTextObject.builder()
                .text("*리뷰이*\n" + participants.authorText())
                .build());
        fields.add(MarkdownTextObject.builder()
                .text("*리뷰어*\n" + participants.pendingReviewersText())
                .build());

        return SectionBlock.builder()
                           .fields(fields)
                           .build();
    }

    private ActionsBlock buildActionButtons(ReviewAssignmentRequest report, String actionMetaJson) {
        List<BlockElement> elements = new ArrayList<>();

        if (actionMetaJson != null && !actionMetaJson.isBlank()) {
            elements.add(ButtonElement.builder()
                                      .text(PlainTextObject.builder().text("리뷰 예약").build())
                                      .actionId(SCHEDULE_ACTION_ID)
                                      .value(actionMetaJson)
                                      .build());
        }
        elements.add(ButtonElement.builder()
                .text(PlainTextObject.builder().text("리뷰 바로 시작").build())
                .style("primary")
                .actionId(START_REVIEW_ACTION_ID)
                .url(report.pullRequestUrl())
                .build());
        return ActionsBlock.builder()
                           .elements(elements)
                           .build();
    }

    private ActionsBlock buildClaimButtons(List<String> unmappedGithubIds) {
        List<BlockElement> elements = unmappedGithubIds.stream()
                .map(githubId -> (BlockElement) ButtonElement.builder()
                        .text(PlainTextObject
                                .builder()
                                .text("\uD83D\uDE4B\u200D️ " + githubId + "는 저예요")
                                .build()
                        )
                        .value(githubId)
                        .actionId(CLAIM_ACTION_PREFIX + githubId)
                        .build())
                .toList();

        return ActionsBlock.builder()
                           .elements(elements)
                           .build();
    }

    private String buildFallbackText(ReviewAssignmentRequest event) {
        return "🚀 New PR: " + event.pullRequestTitle() + " (#" + event.pullRequestNumber() + ")";
    }

    private JsonNode toJsonNode(Object value) {
        String json = GsonFactory.createSnakeCase().toJson(value);

        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Slack 블록 JSON 변환 실패", e);
        }
    }
}
