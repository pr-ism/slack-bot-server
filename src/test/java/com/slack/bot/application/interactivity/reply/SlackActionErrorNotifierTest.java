package com.slack.bot.application.interactivity.reply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.verify;

import com.slack.bot.application.interactivity.client.NotificationApiClient;
import java.util.Collections;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class SlackActionErrorNotifierTest {

    @Mock
    NotificationApiClient notificationApiClient;

    @Test
    void 에러_메시지를_에페메랄로_전송한다() {
        // given
        SlackActionErrorNotifier notifier = new SlackActionErrorNotifier(notificationApiClient);

        // when
        notifier.notify("xoxb", "C1", "U1", InteractivityErrorType.INVALID_META);

        // then
        verify(notificationApiClient).sendEphemeralMessage(
                "xoxb",
                "C1",
                "U1",
                InteractivityErrorType.INVALID_META.message()
        );
    }

    @Test
    void 에러_알림_후_빈_응답을_반환한다() {
        // given
        SlackActionErrorNotifier notifier = new SlackActionErrorNotifier(notificationApiClient);

        // when
        Object response = notifier.respond("xoxb", "C1", "U1", InteractivityErrorType.RESERVATION_NOT_FOUND);

        // then
        assertAll(
                () -> verify(notificationApiClient).sendEphemeralMessage(
                        "xoxb",
                        "C1",
                        "U1",
                        InteractivityErrorType.RESERVATION_NOT_FOUND.message()
                ),
                () -> assertThat(response).isEqualTo(Collections.emptyMap())
        );
    }
}
