-- CampusCare production workflow upgrade. Safe to run more than once.
-- Run in Supabase SQL Editor before deploying the matching backend release.

ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS stream_name VARCHAR(40),
    ADD COLUMN IF NOT EXISTS semester INTEGER;

-- MCS was a mistaken label for MCA. Normalize existing rows and replace the check constraint.
ALTER TABLE public.users DROP CONSTRAINT IF EXISTS ck_users_academic_profile;
UPDATE public.users SET stream_name = 'MCA' WHERE stream_name = 'MCS';
ALTER TABLE public.users ADD CONSTRAINT ck_users_academic_profile CHECK (
    (stream_name IS NULL AND semester IS NULL)
    OR (stream_name IS NOT NULL AND btrim(stream_name) <> ''
        AND stream_name IN ('MSS', 'MCA', 'PGDM', 'MBA') AND semester IS NOT NULL AND semester IN (1, 2))
);

CREATE INDEX IF NOT EXISTS idx_users_student_stream_semester
    ON public.users (stream_name, semester) WHERE role = 'STUDENT';

ALTER TABLE public.complaints
    ADD COLUMN IF NOT EXISTS removed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS removed_by_user_id UUID,
    ADD COLUMN IF NOT EXISTS removal_reason TEXT,
    ADD COLUMN IF NOT EXISTS assigned_to_user_id UUID;

ALTER TABLE public.complaint_status_history
    ADD COLUMN IF NOT EXISTS changed_by_user_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_complaints_removed_by' AND conrelid = 'public.complaints'::regclass) THEN
        ALTER TABLE public.complaints ADD CONSTRAINT fk_complaints_removed_by
            FOREIGN KEY (removed_by_user_id) REFERENCES public.users (user_id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_complaints_assigned_to' AND conrelid = 'public.complaints'::regclass) THEN
        ALTER TABLE public.complaints ADD CONSTRAINT fk_complaints_assigned_to
            FOREIGN KEY (assigned_to_user_id) REFERENCES public.users (user_id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_status_history_changed_by' AND conrelid = 'public.complaint_status_history'::regclass) THEN
        ALTER TABLE public.complaint_status_history ADD CONSTRAINT fk_status_history_changed_by
            FOREIGN KEY (changed_by_user_id) REFERENCES public.users (user_id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_complaints_removal_metadata' AND conrelid = 'public.complaints'::regclass) THEN
        ALTER TABLE public.complaints ADD CONSTRAINT ck_complaints_removal_metadata CHECK (
            (removed_at IS NULL AND removed_by_user_id IS NULL AND removal_reason IS NULL)
            OR (removed_at IS NOT NULL AND removed_by_user_id IS NOT NULL AND removal_reason IS NOT NULL)
        );
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS public.complaint_comments (
    comment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    complaint_id UUID NOT NULL REFERENCES public.complaints (complaint_id) ON DELETE RESTRICT,
    author_user_id UUID NOT NULL REFERENCES public.users (user_id) ON DELETE RESTRICT,
    body TEXT NOT NULL CHECK (length(trim(body)) BETWEEN 1 AND 4000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS public.complaint_revisions (
    revision_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    complaint_id UUID NOT NULL REFERENCES public.complaints (complaint_id) ON DELETE RESTRICT,
    edited_by_user_id UUID NOT NULL REFERENCES public.users (user_id) ON DELETE RESTRICT,
    old_title VARCHAR(200) NOT NULL,
    old_description TEXT NOT NULL,
    old_category_id UUID NOT NULL REFERENCES public.complaint_categories (category_id) ON DELETE RESTRICT,
    old_classroom_id UUID NOT NULL REFERENCES public.classrooms (classroom_id) ON DELETE RESTRICT,
    edited_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS public.complaint_assignment_history (
    assignment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    complaint_id UUID NOT NULL REFERENCES public.complaints (complaint_id) ON DELETE RESTRICT,
    previous_assignee_user_id UUID REFERENCES public.users (user_id) ON DELETE RESTRICT,
    new_assignee_user_id UUID REFERENCES public.users (user_id) ON DELETE RESTRICT,
    changed_by_user_id UUID NOT NULL REFERENCES public.users (user_id) ON DELETE RESTRICT,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS public.notifications (
    notification_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users (user_id) ON DELETE RESTRICT,
    complaint_id UUID REFERENCES public.complaints (complaint_id) ON DELETE RESTRICT,
    notification_type VARCHAR(40) NOT NULL,
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS public.auth_action_tokens (
    token_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users (user_id) ON DELETE CASCADE,
    purpose VARCHAR(24) NOT NULL CHECK (purpose IN ('VERIFY_EMAIL', 'PASSWORD_RESET')),
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_complaints_assignee_status ON public.complaints (assigned_to_user_id, status) WHERE removed_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_complaint_comments_complaint_created ON public.complaint_comments (complaint_id, created_at);
CREATE INDEX IF NOT EXISTS idx_complaint_revisions_complaint_edited ON public.complaint_revisions (complaint_id, edited_at DESC);
CREATE INDEX IF NOT EXISTS idx_assignment_history_complaint_changed ON public.complaint_assignment_history (complaint_id, changed_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread ON public.notifications (user_id, created_at DESC) WHERE read_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_auth_action_tokens_user_purpose ON public.auth_action_tokens (user_id, purpose, expires_at DESC);
CREATE INDEX IF NOT EXISTS idx_auth_action_tokens_user_created ON public.auth_action_tokens (user_id, purpose, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_complaints_active_department_created_at ON public.complaints (department_id, created_at DESC) WHERE removed_at IS NULL;

ALTER TABLE public.complaint_comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.complaint_revisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.complaint_assignment_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.auth_action_tokens ENABLE ROW LEVEL SECURITY;
