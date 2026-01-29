package com.slack.bot.application.event.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.event.parser.dto.MemberJoinedEventPayload;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MemberJoinedEventParserTest {

    private final MemberJoinedEventParser parser = new MemberJoinedEventParser();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 모든_필드_값이_존재하는_정상적인_이벤트_JSON_응답을_parsing한다() throws Exception {
        // given
        String json = """
                {
                    "team_id": "T12345",
                    "event": {
                        "user": "U98765",
                        "channel": "C12345",
                        "inviter": "U11111"
                    }
                }
                """;
        JsonNode payload = objectMapper.readTree(json);

        // when
        MemberJoinedEventPayload actual = parser.parse(payload);

        // then
        assertAll(
                () -> assertThat(actual.teamId()).isEqualTo("T12345"),
                () -> assertThat(actual.joinedUserId()).isEqualTo("U98765"),
                () -> assertThat(actual.channelId()).isEqualTo("C12345"),
                () -> assertThat(actual.inviterId()).isEqualTo("U11111")
        );
    }

    @Test
    void channel_name과_inviter_필드가_비어_있는_정상적인_이벤트_JSON_응답을_parsing한다() throws JsonProcessingException {
        // given
        String json = """
                {
                    "team_id": "T12345",
                    "event": {
                        "user": "U98765",
                        "channel": "C12345"
                    }
                }
                """;
        JsonNode payload = objectMapper.readTree(json);

        // when
        MemberJoinedEventPayload actual = parser.parse(payload);

        // then
        assertAll(
                () -> assertThat(actual.teamId()).isEqualTo("T12345"),
                () -> assertThat(actual.inviterId()).isNull()
        );
    }
}
