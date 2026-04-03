INSERT INTO slack_interaction_inbox (
    id,
    created_at,
    updated_at,
    interaction_type,
    idempotency_key,
    payload_json,
    status,
    processing_attempt,
    processing_lease_state,
    processed_time_state,
    failed_time_state,
    failure_state
) VALUES (
    200,
    NOW(),
    NOW(),
    'BLOCK_ACTIONS',
    'BLOCK-ACTION-TIMEOUT-200',
    '{"team":{"id":"T1"},"channel":{"id":"C1"},"user":{"id":"U1"},"actions":[{"action_id":"cancel_review_reservation","value":"100"}]}',
    'PROCESSING',
    1,
    'CLAIMED',
    'ABSENT',
    'ABSENT',
    'ABSENT'
);

INSERT INTO slack_interaction_inbox_processing_lease_details (
    owner_id,
    started_at
) VALUES (
    200,
    DATEADD('MINUTE', -10, NOW())
);
