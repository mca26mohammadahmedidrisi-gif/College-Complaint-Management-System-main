-- Add audited, reversible moderation fields to an existing complaints table.
-- Run once in Supabase SQL Editor before deploying the backend version that maps them.
ALTER TABLE public.complaints
    ADD COLUMN IF NOT EXISTS removed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS removed_by_user_id UUID,
    ADD COLUMN IF NOT EXISTS removal_reason TEXT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_complaints_removed_by'
          AND conrelid = 'public.complaints'::regclass
    ) THEN
        ALTER TABLE public.complaints
            ADD CONSTRAINT fk_complaints_removed_by
            FOREIGN KEY (removed_by_user_id) REFERENCES public.users (user_id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_complaints_removal_metadata'
          AND conrelid = 'public.complaints'::regclass
    ) THEN
        ALTER TABLE public.complaints
            ADD CONSTRAINT ck_complaints_removal_metadata CHECK (
                (removed_at IS NULL AND removed_by_user_id IS NULL AND removal_reason IS NULL)
                OR (removed_at IS NOT NULL AND removed_by_user_id IS NOT NULL AND removal_reason IS NOT NULL)
            );
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_complaints_active_department_created_at
    ON public.complaints (department_id, created_at DESC) WHERE removed_at IS NULL;
