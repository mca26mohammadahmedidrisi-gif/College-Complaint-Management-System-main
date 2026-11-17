-- One-time repair for existing reports whose department was never set.
-- Existing non-null department routing is preserved.
UPDATE public.complaints AS complaint
SET department_id = category.department_id,
    updated_at = CURRENT_TIMESTAMP
FROM public.complaint_categories AS category
WHERE complaint.category_id = category.category_id
  AND complaint.department_id IS NULL
  AND category.department_id IS NOT NULL;
