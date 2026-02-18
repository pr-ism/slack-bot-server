package com.slack.bot.application.interactivity.box.retry;

import com.slack.bot.application.interactivity.client.exception.SlackBotMessageDispatchException;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;
import java.util.Map;
import org.springframework.web.client.ResourceAccessException;

public class InteractionRetryExceptionClassifier {

    private final Set<Class<? extends Throwable>> retryableExceptionTypes;

    public static InteractionRetryExceptionClassifier create() {
        Set<Class<? extends Throwable>> retryableExceptionTypes = createRetryableExceptionTypes();

        return new InteractionRetryExceptionClassifier(retryableExceptionTypes);
    }

    private static Set<Class<? extends Throwable>> createRetryableExceptionTypes() {
        Set<Class<? extends Throwable>> retryableTypes = new HashSet<>();

        retryableTypes.add(SlackBotMessageDispatchException.class);
        retryableTypes.add(ResourceAccessException.class);
        return Set.copyOf(retryableTypes);
    }

    private InteractionRetryExceptionClassifier(Set<Class<? extends Throwable>> retryableExceptionTypes) {
        this.retryableExceptionTypes = retryableExceptionTypes;
    }

    public Map<Class<? extends Throwable>, Boolean> getRetryableExceptions() {
        Map<Class<? extends Throwable>, Boolean> retryables = new HashMap<>();
        for (Class<? extends Throwable> retryableType : retryableExceptionTypes) {
            retryables.put(retryableType, true);
        }
        return Map.copyOf(retryables);
    }

    public boolean isRetryable(Throwable throwable) {
        Throwable cursor = throwable;

        while (cursor != null) {
            for (Class<? extends Throwable> retryableType : retryableExceptionTypes) {
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
