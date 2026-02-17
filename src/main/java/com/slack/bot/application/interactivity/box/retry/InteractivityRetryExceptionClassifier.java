package com.slack.bot.application.interactivity.box.retry;

import com.slack.bot.application.interactivity.client.exception.SlackBotMessageDispatchException;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import org.springframework.web.client.ResourceAccessException;

@Getter
public class InteractivityRetryExceptionClassifier {

    private final Map<Class<? extends Throwable>, Boolean> retryableExceptions;

    public static InteractivityRetryExceptionClassifier create() {
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = createRetryableExceptions();

        return new InteractivityRetryExceptionClassifier(retryableExceptions);
    }

    private static Map<Class<? extends Throwable>, Boolean> createRetryableExceptions() {
        Map<Class<? extends Throwable>, Boolean> retryables = new HashMap<>();

        retryables.put(SlackBotMessageDispatchException.class, true);
        retryables.put(ResourceAccessException.class, true);
        return Map.copyOf(retryables);
    }

    private InteractivityRetryExceptionClassifier(Map<Class<? extends Throwable>, Boolean> retryableExceptions) {
        this.retryableExceptions = retryableExceptions;
    }

    public boolean isRetryable(Throwable throwable) {
        Throwable cursor = throwable;

        while (cursor != null) {
            for (Class<? extends Throwable> retryableType : retryableExceptions.keySet()) {
                if (retryableType.isAssignableFrom(cursor.getClass())) {
                    return true;
                }
            }

            Throwable next = cursor.getCause();
            if (next == cursor) {
                break;
            }
            cursor = next;
        }

        return false;
    }
}
