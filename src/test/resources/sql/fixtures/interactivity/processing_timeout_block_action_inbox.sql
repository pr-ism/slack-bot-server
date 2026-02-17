INSERT INTO slack_interaction_inbox (
    id,
    created_at,
    updated_at,
    interaction_type,
    idempotency_key,
    payload_json,
    status,
    processing_attempt,
    processing_started_at,
    processed_at,
    failed_at,
    failure_reason,
    failure_type
) VALUES (
    200,
    NOW(),
    NOW(),
    'BLOCK_ACTIONS',
    'BLOCK-ACTION-TIMEOUT-200',
    '{"team":{"id":"T1"},"channel":{"id":"C1"},"user":{"id":"U1"},"actions":[{"action_id":"cancel_review_reservation","value":"100"}]}',
    'PROCESSING',
    1,
    DATEADD('MINUTE', -10, NOW()),
    NULL,
    NULL,
    NULL,
    NULL
);
