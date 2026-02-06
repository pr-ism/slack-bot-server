package com.slack.bot.application.interactivity.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interactivity.dto.ReviewScheduleMetaDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.bot.application.interactivity.reservation.exception.ReservationMetaInvalidException;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReservationMetaResolverTest {

    @Autowired
    ReservationMetaResolver reservationMetaResolver;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void 예약_관련_메타데이터를_정상적으로_파싱한다() {
        // given
        String metaJson = """
                {
                  "team_id": "T1",
                  "channel_id": "C1",
                  "project_id": "123",
                  "pull_request_id": 10,
                  "pull_request_number": 10,
                  "pull_request_title": "PR 제목",
                  "pull_request_url": "https://github.com/org/repo/pull/10",
                  "author_github_id": "author-gh",
                  "author_slack_id": "U_AUTHOR",
                  "reservation_id": "R1"
                }
                """;

        // when
        ReviewScheduleMetaDto actual = reservationMetaResolver.parseMeta(metaJson);

        // then
        assertAll(
                () -> assertThat(actual.teamId()).isEqualTo("T1"),
                () -> assertThat(actual.channelId()).isEqualTo("C1"),
                () -> assertThat(actual.projectId()).isEqualTo("123"),
                () -> assertThat(actual.pullRequestId()).isEqualTo(10L),
                () -> assertThat(actual.pullRequestNumber()).isEqualTo(10),
                () -> assertThat(actual.pullRequestTitle()).isEqualTo("PR 제목"),
                () -> assertThat(actual.pullRequestUrl()).isEqualTo("https://github.com/org/repo/pull/10"),
                () -> assertThat(actual.authorGithubId()).isEqualTo("author-gh"),
                () -> assertThat(actual.authorSlackId()).isEqualTo("U_AUTHOR"),
                () -> assertThat(actual.reservationId()).isEqualTo("R1")
        );
    }

    private static Stream<Arguments> missingRequiredFieldMetas() {
        return Stream.of(
                Arguments.of("team_id", (Consumer<ObjectNode>) node -> node.remove("team_id")),
                Arguments.of("channel_id", (Consumer<ObjectNode>) node -> node.remove("channel_id")),
                Arguments.of("project_id", (Consumer<ObjectNode>) node -> node.remove("project_id")),
                Arguments.of("pull_request_id", (Consumer<ObjectNode>) node -> node.remove("pull_request_id")),
                Arguments.of("pull_request_number", (Consumer<ObjectNode>) node -> node.remove("pull_request_number")),
                Arguments.of("pull_request_title", (Consumer<ObjectNode>) node -> node.remove("pull_request_title")),
                Arguments.of("pull_request_url", (Consumer<ObjectNode>) node -> node.remove("pull_request_url")),
                Arguments.of("author_github_id", (Consumer<ObjectNode>) node -> node.remove("author_github_id")),
                Arguments.of("author_slack_id", (Consumer<ObjectNode>) node -> node.remove("author_slack_id")),
                Arguments.of("reservation_id", (Consumer<ObjectNode>) node -> node.remove("reservation_id"))
        );
    }

    @ParameterizedTest
    @MethodSource("missingRequiredFieldMetas")
    void 필수_필드가_없으면_예외를_던진다(String field, Consumer<ObjectNode> mutator) {
        // given
        String metaJson = createMetaJson(mutator);

        // when & then
        assertThatThrownBy(() -> reservationMetaResolver.parseMeta(metaJson))
                .isInstanceOf(ReservationMetaInvalidException.class)
                .hasMessage(field + "는 비어 있을 수 없습니다.");
    }

    private static Stream<Arguments> blankRequiredFieldMetas() {
        return Stream.of(
                Arguments.of("team_id", (Consumer<ObjectNode>) node -> node.put("team_id", " ")),
                Arguments.of("channel_id", (Consumer<ObjectNode>) node -> node.put("channel_id", " ")),
                Arguments.of("project_id", (Consumer<ObjectNode>) node -> node.put("project_id", " ")),
                Arguments.of("pull_request_title", (Consumer<ObjectNode>) node -> node.put("pull_request_title", " ")),
                Arguments.of("pull_request_url", (Consumer<ObjectNode>) node -> node.put("pull_request_url", " ")),
                Arguments.of("author_github_id", (Consumer<ObjectNode>) node -> node.put("author_github_id", " ")),
                Arguments.of("author_slack_id", (Consumer<ObjectNode>) node -> node.put("author_slack_id", " ")),
                Arguments.of("reservation_id", (Consumer<ObjectNode>) node -> node.put("reservation_id", " "))
        );
    }

    @ParameterizedTest
    @MethodSource("blankRequiredFieldMetas")
    void 필수_문자열_필드가_공백이면_예외를_던진다(String field, Consumer<ObjectNode> mutator) {
        // given
        String metaJson = createMetaJson(mutator);

        // when & then
        assertThatThrownBy(() -> reservationMetaResolver.parseMeta(metaJson))
                .isInstanceOf(ReservationMetaInvalidException.class)
                .hasMessage(field + "는 비어 있을 수 없습니다.");
    }

    @Test
    void 잘못된_메타는_예외를_던진다() {
        // given
        String invalidMeta = "{invalid-json";

        // when & then
        assertThatThrownBy(() -> reservationMetaResolver.parseMeta(invalidMeta))
                .isInstanceOf(ReservationMetaInvalidException.class)
                .hasMessage("메타데이터 파싱 실패");
    }

    private String createMetaJson(Consumer<ObjectNode> mutator) {
        ObjectNode node = objectMapper.createObjectNode();

        node.put("team_id", "T1");
        node.put("channel_id", "C1");
        node.put("project_id", "123");
        node.put("pull_request_id", 10);
        node.put("pull_request_number", 10);
        node.put("pull_request_title", "PR 제목");
        node.put("pull_request_url", "https://github.com/org/repo/pull/10");
        node.put("author_github_id", "author-gh");
        node.put("author_slack_id", "U_AUTHOR");
        node.put("reservation_id", "R1");
        mutator.accept(node);

        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("테스트 메타데이터 생성 실패", e);
        }
    }
}
