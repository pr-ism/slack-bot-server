package com.slack.bot.application;

import com.slack.bot.application.event.handler.SlackEventHandlerRegistry;
import com.slack.bot.application.interactivity.reservation.ReviewReservationCoordinator;
import com.slack.bot.context.CleanupExecutionListener;
import com.slack.bot.application.interactivity.client.NotificationApiClient;
import com.slack.bot.domain.auth.TokenDecoder;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Clock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.slack.bot.application.interactivity.publisher.ReviewInteractionEventPublisher;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@Import(IntegrationTestConfig.class)
@MockitoBean(types = {
        DateTimeProvider.class,
        TokenDecoder.class,
        SlackEventHandlerRegistry.class,
        NotificationApiClient.class
})
@MockitoSpyBean(types = {
        Clock.class,
        ReviewInteractionEventPublisher.class,
        ReviewReservationCoordinator.class
})
@TestExecutionListeners(listeners = CleanupExecutionListener.class, mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public @interface IntegrationTest {
}
