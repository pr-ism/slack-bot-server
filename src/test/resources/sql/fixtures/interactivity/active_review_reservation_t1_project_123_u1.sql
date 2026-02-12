INSERT INTO review_reservations (
    id,
    created_at,
    updated_at,
    team_id,
    channel_id,
    project_id,
    pull_request_id,
    pull_request_number,
    pull_request_title,
    pull_request_url,
    author_slack_id,
    reviewer_slack_id,
    scheduled_at,
    status
) VALUES (
    100,
    '2024-01-01 00:00:00',
    '2024-01-01 00:00:00',
    'T1',
    'C1',
    123,
    10,
    10,
    'PR 제목',
    'https://github.com/org/repo/pull/10',
    'U_AUTHOR',
    'U1',
    '2099-01-01 00:00:00',
    'ACTIVE'
);

INSERT INTO project_members (
    id,
    team_id,
    slack_user_id,
    display_name,
    github_id,
    created_at,
    updated_at
) VALUES (
    900,
    'T1',
    'U_AUTHOR',
    '작성자',
    'author-gh',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
