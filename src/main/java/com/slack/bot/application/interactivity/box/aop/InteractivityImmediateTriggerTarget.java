package com.slack.bot.application.interactivity.box.aop;

import com.slack.bot.application.interactivity.box.InteractionImmediateProcessor;

public enum InteractivityImmediateTriggerTarget {

    BLOCK_ACTION_INBOX {
        @Override
        public void trigger(InteractionImmediateProcessor interactionImmediateProcessor) {
            interactionImmediateProcessor.triggerBlockActionInbox();
        }
    },
    VIEW_SUBMISSION_INBOX {
        @Override
        public void trigger(InteractionImmediateProcessor interactionImmediateProcessor) {
            interactionImmediateProcessor.triggerViewSubmissionInbox();
        }
    },
    OUTBOX {
        @Override
        public void trigger(InteractionImmediateProcessor interactionImmediateProcessor) {
            interactionImmediateProcessor.triggerOutbox();
        }
    };

    public abstract void trigger(InteractionImmediateProcessor interactionImmediateProcessor);
}
