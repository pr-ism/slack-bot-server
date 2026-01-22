package com.slack.bot.docs.snippet.exceptions;

import org.springframework.http.HttpStatus;

public record ExceptionContent(HttpStatus httpStatus, String message) {
}
