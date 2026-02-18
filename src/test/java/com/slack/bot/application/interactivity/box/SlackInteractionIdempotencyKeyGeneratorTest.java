package com.slack.bot.application.interactivity.box;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackInteractionIdempotencyKeyGeneratorTest {

    @Test
    void 같은_입력이면_같은_해시를_생성한다() {
        // given
        SlackInteractionIdempotencyKeyGenerator generator = new SlackInteractionIdempotencyKeyGenerator();

        // when
        String first = generator.generate(
                SlackInteractionIdempotencyScope.BLOCK_ACTIONS,
                "{\"type\":\"block_actions\"}"
        );
        String second = generator.generate(
                SlackInteractionIdempotencyScope.BLOCK_ACTIONS,
                "{\"type\":\"block_actions\"}"
        );

        // then
        assertThat(first).isEqualTo(second);
    }

    @Test
    void 서로_다른_입력이면_다른_해시를_생성한다() {
        // given
        SlackInteractionIdempotencyKeyGenerator generator = new SlackInteractionIdempotencyKeyGenerator();

        // when
        String base = generator.generate(
                SlackInteractionIdempotencyScope.BLOCK_ACTIONS,
                "{\"type\":\"block_actions\"}"
        );
        String typeDifferent = generator.generate(
                SlackInteractionIdempotencyScope.VIEW_SUBMISSION,
                "{\"type\":\"block_actions\"}"
        );
        String payloadDifferent = generator.generate(
                SlackInteractionIdempotencyScope.BLOCK_ACTIONS,
                "{\"type\":\"view_submission\"}"
        );

        // then
        assertThat(base)
                .isNotEqualTo(typeDifferent)
                .isNotEqualTo(payloadDifferent);
    }

    static Stream<Arguments> provideNullAndEmptyArgs() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of(null, "")
        );
    }

    @ParameterizedTest(name = "입력이 {0}, {1} 일 때 빈 문자열과 동일한 해시를 생성한다")
    @MethodSource("provideNullAndEmptyArgs")
    void 비어_있는_문자열_입력은_동일한_해시를_생성한다(SlackInteractionIdempotencyScope scope, String payload) {
        // given
        SlackInteractionIdempotencyKeyGenerator generator = new SlackInteractionIdempotencyKeyGenerator();

        // when
        String actualHash = generator.generate(scope, payload);

        // then
        String expectedHash = generator.generate(scope, "");

        assertThat(actualHash)
                .isEqualTo(expectedHash)
                .hasSize(64)
                .matches("^[0-9a-f]+$");
    }

    @Test
    void SHA_256_알고리즘_획득에_실패하면_IllegalStateException을_던진다() {
        // given
        try (MockedStatic<MessageDigest> mocked = mockStatic(MessageDigest.class)) {
            SlackInteractionIdempotencyKeyGenerator generator = new SlackInteractionIdempotencyKeyGenerator();

            mocked.when(() -> MessageDigest.getInstance("SHA-256"))
                  .thenThrow(new NoSuchAlgorithmException("missing"));

            // when & then
            assertThatThrownBy(() -> generator.generate(SlackInteractionIdempotencyScope.BLOCK_ACTIONS, "{}"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("SHA-256 알고리즘을 찾을 수 없습니다.");
        }
    }
}
