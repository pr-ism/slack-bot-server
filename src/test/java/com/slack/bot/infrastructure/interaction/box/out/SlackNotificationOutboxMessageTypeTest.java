package com.slack.bot.infrastructure.interaction.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackNotificationOutboxMessageTypeTest {

    @ParameterizedTest
    @MethodSource("messageTypeSupportArguments")
    void supports_조합이_메시지_타입별_계약과_일치한다(
            SlackNotificationOutboxMessageType messageType,
            boolean supportsUserId,
            boolean supportsText,
            boolean supportsBlocksJson,
            boolean supportsFallbackText
    ) {
        // when & then
        assertAll(
                () -> assertThat(messageType.supportsUserId()).isEqualTo(supportsUserId),
                () -> assertThat(messageType.supportsText()).isEqualTo(supportsText),
                () -> assertThat(messageType.supportsBlocksJson()).isEqualTo(supportsBlocksJson),
                () -> assertThat(messageType.supportsFallbackText()).isEqualTo(supportsFallbackText)
        );
    }

    @ParameterizedTest
    @MethodSource("dispatchRouteArguments")
    void dispatch는_메시지_타입별_전송_경로만_호출한다(
            SlackNotificationOutboxMessageType messageType,
            String expectedRoute
    ) throws JsonProcessingException {
        // given
        AtomicReference<String> actualRoute = new AtomicReference<>();
        AtomicInteger calledCount = new AtomicInteger();
        SlackNotificationOutboxMessageType.Dispatcher dispatcher = SlackNotificationOutboxMessageType.Dispatcher.builder()
                                                                                                               .dispatchEphemeralText(
                                                                                                                       () -> recordRoute(
                                                                                                                               actualRoute,
                                                                                                                               calledCount,
                                                                                                                               "EPHEMERAL_TEXT"
                                                                                                                       )
                                                                                                               )
                                                                                                               .dispatchEphemeralBlocks(
                                                                                                                       () -> recordRoute(
                                                                                                                               actualRoute,
                                                                                                                               calledCount,
                                                                                                                               "EPHEMERAL_BLOCKS"
                                                                                                                       )
                                                                                                               )
                                                                                                               .dispatchChannelText(
                                                                                                                       () -> recordRoute(
                                                                                                                               actualRoute,
                                                                                                                               calledCount,
                                                                                                                               "CHANNEL_TEXT"
                                                                                                                       )
                                                                                                               )
                                                                                                               .dispatchChannelBlocks(
                                                                                                                       () -> recordRoute(
                                                                                                                               actualRoute,
                                                                                                                               calledCount,
                                                                                                                               "CHANNEL_BLOCKS"
                                                                                                                       )
                                                                                                               )
                                                                                                               .build();

        // when
        messageType.dispatch(dispatcher);

        // then
        assertAll(
                () -> assertThat(actualRoute.get()).isEqualTo(expectedRoute),
                () -> assertThat(calledCount.get()).isEqualTo(1)
        );
    }

    @ParameterizedTest
    @MethodSource("missingDispatcherActionArguments")
    void dispatcher_builder는_필수_경로가_빠지면_예외를_던진다(
            String missingActionName,
            SlackNotificationOutboxMessageType.DispatchBuilder builder
    ) {
        // when & then
        assertThatThrownBy(() -> builder.build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(missingActionName + "가 설정되지 않았습니다.");
    }

    private static Stream<Arguments> messageTypeSupportArguments() {
        return Stream.of(
                Arguments.of(SlackNotificationOutboxMessageType.EPHEMERAL_TEXT, true, true, false, false),
                Arguments.of(SlackNotificationOutboxMessageType.EPHEMERAL_BLOCKS, true, false, true, true),
                Arguments.of(SlackNotificationOutboxMessageType.CHANNEL_TEXT, false, true, false, false),
                Arguments.of(SlackNotificationOutboxMessageType.CHANNEL_BLOCKS, false, false, true, true)
        );
    }

    private static Stream<Arguments> dispatchRouteArguments() {
        return Stream.of(
                Arguments.of(SlackNotificationOutboxMessageType.EPHEMERAL_TEXT, "EPHEMERAL_TEXT"),
                Arguments.of(SlackNotificationOutboxMessageType.EPHEMERAL_BLOCKS, "EPHEMERAL_BLOCKS"),
                Arguments.of(SlackNotificationOutboxMessageType.CHANNEL_TEXT, "CHANNEL_TEXT"),
                Arguments.of(SlackNotificationOutboxMessageType.CHANNEL_BLOCKS, "CHANNEL_BLOCKS")
        );
    }

    private static Stream<Arguments> missingDispatcherActionArguments() {
        return Stream.of(
                Arguments.of(
                        "dispatchEphemeralText",
                        SlackNotificationOutboxMessageType.Dispatcher.builder()
                                                                     .dispatchEphemeralBlocks(() -> { })
                                                                     .dispatchChannelText(() -> { })
                                                                     .dispatchChannelBlocks(() -> { })
                ),
                Arguments.of(
                        "dispatchEphemeralBlocks",
                        SlackNotificationOutboxMessageType.Dispatcher.builder()
                                                                     .dispatchEphemeralText(() -> { })
                                                                     .dispatchChannelText(() -> { })
                                                                     .dispatchChannelBlocks(() -> { })
                ),
                Arguments.of(
                        "dispatchChannelText",
                        SlackNotificationOutboxMessageType.Dispatcher.builder()
                                                                     .dispatchEphemeralText(() -> { })
                                                                     .dispatchEphemeralBlocks(() -> { })
                                                                     .dispatchChannelBlocks(() -> { })
                ),
                Arguments.of(
                        "dispatchChannelBlocks",
                        SlackNotificationOutboxMessageType.Dispatcher.builder()
                                                                     .dispatchEphemeralText(() -> { })
                                                                     .dispatchEphemeralBlocks(() -> { })
                                                                     .dispatchChannelText(() -> { })
                )
        );
    }

    private static void recordRoute(
            AtomicReference<String> actualRoute,
            AtomicInteger calledCount,
            String route
    ) {
        actualRoute.set(route);
        calledCount.incrementAndGet();
    }
}
