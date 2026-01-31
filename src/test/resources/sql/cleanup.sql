SET REFERENTIAL_INTEGRITY FALSE;

TRUNCATE TABLE workspaces;
TRUNCATE TABLE oauth_verification_states;
TRUNCATE TABLE project_members;
TRUNCATE TABLE channels;
TRUNCATE TABLE access_links;
TRUNCATE TABLE access_link_sequences;
TRUNCATE TABLE notification_settings;

SET REFERENTIAL_INTEGRITY TRUE;
