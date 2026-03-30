package com.slack.bot.infrastructure.interaction.box.in;

public enum SlackInteractionInboxType {
    BLOCK_ACTIONS,
    VIEW_SUBMISSION;

    public boolean isBlockActions() {
        return this == BLOCK_ACTIONS;
    }

    public boolean isViewSubmission() {
        return this == VIEW_SUBMISSION;
    }
}
