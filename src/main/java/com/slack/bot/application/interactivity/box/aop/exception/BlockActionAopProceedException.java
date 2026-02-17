package com.slack.bot.application.interactivity.box.aop.exception;

public class BlockActionAopProceedException extends RuntimeException {

    public BlockActionAopProceedException(String stage, Throwable cause) {
        super("block action enqueue AOP proceed 실패. stage=" + stage, cause);
    }
}
