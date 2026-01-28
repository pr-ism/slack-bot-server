package com.slack.bot.presentation.command;

import com.slack.bot.application.command.CommandService;
import com.slack.bot.application.command.dto.request.SlackCommandRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/slack/commands")
@RequiredArgsConstructor
public class CommandController {

    private final CommandService commandService;

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> handleCommand(SlackCommandRequest request) {
        String message = commandService.handle(request);

        return ResponseEntity.ok(message);
    }
}
