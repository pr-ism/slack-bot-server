package com.slack.bot.application.interaction.box.retry;

import static org.assertj.core.api.Assertions.assertThat;

import com.slack.bot.application.interaction.client.exception.SlackBotMessageDispatchException;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class InteractionRetryExceptionClassifierTest {

    @Test
    void 슬랙_전송_예외는_retryable로_판단한다() {
        // given
        InteractionRetryExceptionClassifier exceptionClassifier = InteractionRetryExceptionClassifier.create();

        // when
        boolean actual = exceptionClassifier.isRetryable(new SlackBotMessageDispatchException("temporary"));

        // then
        assertThat(actual).isTrue();
    }

    @Test
    void 네트워크_접근_예외는_retryable로_판단한다() {
        // given
        InteractionRetryExceptionClassifier exceptionClassifier = InteractionRetryExceptionClassifier.create();

        // when
        boolean actual = exceptionClassifier.isRetryable(new ResourceAccessException("timeout"));

        // then
        assertThat(actual).isTrue();
    }

    @Test
    void 일반_비즈니스_예외는_non_retryable로_판단한다() {
        // given
        InteractionRetryExceptionClassifier exceptionClassifier = InteractionRetryExceptionClassifier.create();

        // when
        boolean actual = exceptionClassifier.isRetryable(new IllegalArgumentException("invalid"));

        // then
        assertThat(actual).isFalse();
    }

    @Test
    void cause가_자기_자신을_반환해도_무한_루프없이_non_retryable로_판단한다() {
        // given
        InteractionRetryExceptionClassifier exceptionClassifier = InteractionRetryExceptionClassifier.create();

        // when
        boolean actual = exceptionClassifier.isRetryable(new SelfCauseRuntimeException("self-cause"));

        // then
        assertThat(actual).isFalse();
    }

    @Test
    void 일반_비즈니스_예외는_not_retryable로_판단한다() {
        // given
        InteractionRetryExceptionClassifier exceptionClassifier = InteractionRetryExceptionClassifier.create();

        // when
        boolean actual = exceptionClassifier.isNotRetryable(new IllegalArgumentException("invalid"));

        // then
        assertThat(actual).isTrue();
    }

    @Test
    void 슬랙_전송_예외는_not_retryable로_판단하지_않는다() {
        // given
        InteractionRetryExceptionClassifier exceptionClassifier = InteractionRetryExceptionClassifier.create();

        // when
        boolean actual = exceptionClassifier.isNotRetryable(new SlackBotMessageDispatchException("temporary"));

        // then
        assertThat(actual).isFalse();
    }

    private static class SelfCauseRuntimeException extends RuntimeException {

        private SelfCauseRuntimeException(String message) {
            super(message);
        }

        @Override
        public Throwable getCause() {
            return this;
        }
    }
}
