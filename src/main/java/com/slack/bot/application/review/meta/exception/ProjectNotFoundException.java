package com.slack.bot.application.review.meta.exception;

public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(String apiKey) {
        super("프로젝트를 찾을 수 없습니다. apiKey=" + apiKey);
    }
}
