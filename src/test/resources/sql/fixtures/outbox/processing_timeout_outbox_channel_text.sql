INSERT INTO workspaces (id, created_at, updated_at, user_id, team_id, access_token, bot_user_id)
VALUES (1, NOW(), NOW(), 1, 'T1', 'xoxb-test-token', 'B1');

INSERT INTO slack_notification_outbox (
    id,
    message_type,
    idempotency_key,
    team_id,
    channel_id,
    user_id,
    text,
    blocks_json,
    fallback_text,
    status,
    processing_attempt,
    processing_started_at,
    created_at,
    updated_at
) VALUES (
    200,
    'CHANNEL_TEXT',
    'INBOX:200',
    'T1',
    'C1',
    NULL,
    'hello-timeout-200',
    NULL,
    NULL,
    'PROCESSING',
    0,
    DATEADD('MINUTE', -10, NOW()),
    NOW(),
    NOW()
);
