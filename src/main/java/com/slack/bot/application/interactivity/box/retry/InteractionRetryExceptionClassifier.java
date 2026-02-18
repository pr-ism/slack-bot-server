package com.slack.bot.application.interactivity.box.retry;

import com.slack.bot.application.interactivity.client.exception.SlackBotMessageDispatchException;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import org.springframework.web.client.ResourceAccessException;

@Getter
public class InteractionRetryExceptionClassifier {

    private final Map<Class<? extends Throwable>, Boolean> retryableExceptions;

    public static InteractionRetryExceptionClassifier create() {
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = createRetryableExceptions();

        return new InteractionRetryExceptionClassifier(retryableExceptions);
    }

    private static Map<Class<? extends Throwable>, Boolean> createRetryableExceptions() {
        Map<Class<? extends Throwable>, Boolean> retryables = new HashMap<>();

        retryables.put(SlackBotMessageDispatchException.class, true);
        retryables.put(ResourceAccessException.class, true);
        return Map.copyOf(retryables);
    }

    private InteractionRetryExceptionClassifier(Map<Class<? extends Throwable>, Boolean> retryableExceptions) {
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

