package com.slack.bot.application.interaction.box.aop.aspect.exception;

public class ViewSubmissionAopProceedException extends RuntimeException {

    public ViewSubmissionAopProceedException(Throwable cause) {
        super("view submission AOP proceed 실패", cause);
    }
}
