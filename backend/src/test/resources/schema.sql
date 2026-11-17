CREATE TABLE IF NOT EXISTS complaint_comments (
    comment_id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    complaint_id UUID NOT NULL,
    author_user_id UUID NOT NULL,
    body VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS complaint_revisions (
    revision_id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    complaint_id UUID NOT NULL,
    edited_by_user_id UUID NOT NULL,
    old_title VARCHAR(200) NOT NULL,
    old_description CLOB NOT NULL,
    old_category_id UUID NOT NULL,
    old_classroom_id UUID NOT NULL,
    edited_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS complaint_assignment_history (
    assignment_id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    complaint_id UUID NOT NULL,
    previous_assignee_user_id UUID,
    new_assignee_user_id UUID,
    changed_by_user_id UUID NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS notifications (
    notification_id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    user_id UUID NOT NULL,
    complaint_id UUID,
    notification_type VARCHAR(40) NOT NULL,
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE
);
CREATE TABLE IF NOT EXISTS auth_action_tokens (
    token_id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    user_id UUID NOT NULL,
    purpose VARCHAR(24) NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
