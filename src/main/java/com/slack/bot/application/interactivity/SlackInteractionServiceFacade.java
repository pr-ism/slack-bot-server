package com.slack.bot.application.interactivity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.command.exception.WorkspaceNotFoundException;
import com.slack.bot.application.interactivity.reply.dto.response.SlackActionResponse;
import com.slack.bot.application.interactivity.view.ViewSubmissionRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlackInteractionServiceFacade {

    private final ObjectMapper objectMapper;
    private final ViewSubmissionRouter viewSubmissionRouter;
    private final BlockActionInteractionService blockActionInteractionService;

    public SlackActionResponse handle(String payloadJson) {
        try {
            JsonNode payload = objectMapper.readTree(payloadJson);
            String type = resolveInteractionType(payload);

            if ("view_submission".equals(type)) {
                return viewSubmissionRouter.handle(payload);
            }

            blockActionInteractionService.handle(payload);
            return SlackActionResponse.empty();
        } catch (JsonProcessingException e) {
            log.warn("슬랙 인터랙션 payload 파싱에 실패했습니다.", e);
        } catch (WorkspaceNotFoundException e) {
            log.warn("인터랙션 처리 중 워크스페이스를 찾을 수 없습니다.", e);
        } catch (IllegalArgumentException e) {
            log.warn("인터랙션 요청 값이 유효하지 않습니다.", e);
        } catch (RuntimeException e) {
            log.error("슬랙 인터랙션 처리 중 예기치 않은 오류가 발생했습니다.", e);
        }

        return SlackActionResponse.empty();
    }

    private String resolveInteractionType(JsonNode payload) {
        return payload.path("type").asText("block_actions");
    }
}
