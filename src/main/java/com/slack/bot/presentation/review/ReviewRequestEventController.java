package com.slack.bot.presentation.review;

import com.slack.bot.application.review.ReviewEventBatch;
import com.slack.bot.application.review.dto.request.ReviewAssignmentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class ReviewRequestEventController {

    private final ReviewEventBatch eventBatch;

    @PostMapping("/review-request")
    public ResponseEntity<Void> handleReviewRequestEvent(
            @RequestHeader("X-API-Key") String apiKey,
            @Valid @RequestBody ReviewAssignmentRequest request
    ) {
        eventBatch.buffer(apiKey, request);
        return ResponseEntity.ok().build();
    }
}
