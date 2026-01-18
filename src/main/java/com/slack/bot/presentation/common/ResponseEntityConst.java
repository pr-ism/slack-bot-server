package com.slack.bot.presentation.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ResponseEntityConst {

    public static final ResponseEntity<Void> NO_CONTENT = ResponseEntity.noContent().build();
}
