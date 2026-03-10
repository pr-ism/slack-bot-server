package com.slack.bot.support.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class FailingObjectMapper extends ObjectMapper {

    private final String message;

    public FailingObjectMapper() {
        this("직렬화 실패");
    }

    public FailingObjectMapper(String message) {
        this.message = message;
    }

    @Override
    public String writeValueAsString(Object value) throws JsonMappingException {
        throw JsonMappingException.from((JsonParser) null, message);
    }
}
