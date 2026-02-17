package com.slack.bot.application.interactivity.box.aop.exception;

public class BlockActionAopProceedException extends RuntimeException {

    public BlockActionAopProceedException(Throwable cause) {
        super("block action enqueue AOP proceed 실패.", cause);
    }
}
