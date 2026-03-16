package com.slack.bot.application.interaction.box.out.exception;

public class SlackBlocksSerializationException extends RuntimeException {

    public SlackBlocksSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
