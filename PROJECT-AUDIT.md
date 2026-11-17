# CampusCare project audit

Audit date: 18 November 2026

## Student registration and roster

- Student sign-up now requires a stream (MSS, MCA, PGDM, or MBA) and semester (1 or 2). Values are stored on the user record and exposed only in the admin account directory; existing accounts with no academic fields remain valid.
- The admin roster shows the total registered-student count, stream, semester, and a role filter. Apply the updated idempotent production-workflow-upgrade.sql before deploying this schema change.


## Follow-up requirements hardening

- The existing complaint/ownership split, identity-free handler DTOs, department-scoped faculty queue, student tracking, remarks history, and role checks cover the requested anonymous-to-staff workflow. This is privacy from faculty/admin application views, not anonymity from privileged database operators; students should avoid putting identifying details in report text or photos.
- Expanded the repeatable location seed to seven campus levels across Main Building, Science Building, and Sports Complex, with canteen, dining area, labs, library, gym, sports hall, courts, field, and common-area choices. Review the names against the actual campus layout and rerun `seed.sql` in Supabase; do not rerun `schema.sql` on the populated database.
- Frontend and backend now reprocess complaint photos before upload/storage to remove embedded image metadata. Added a backend integration test for EXIF removal.
- Faculty and admin status selectors now offer only transitions the backend accepts. Faculty can review prior updates and add a student-visible comment; student tracking already displays status and comment history.
- Added audited, reversible complaint moderation: department-scoped faculty can remove inappropriate or duplicate reports from their active queue with a required reason. The record, owner mapping, image metadata, and status history stay stored; the student sees the removal reason, while admins retain the audit view.
- Added pending-only student edits with a saved pre-edit revision, a reply thread that anonymizes student authors in staff views, and administrator assignment to an active faculty member in the complaint's department.
- Status history now records the staff actor. Persisted notifications are polled by dashboards and refresh the complaint view; optional email notices use the SMTP provider.
- Added email verification and password reset using single-use, hashed, expiring tokens. Email-dependent paths fail clearly while SMTP is disabled; production email delivery is not active until the backend Vercel project has SMTP settings and `APP_EMAIL_ENABLED=true`.
- Frontend JavaScript syntax checks and the Vercel frontend build pass. Backend Maven tests are present but could not be run locally because Maven is not installed; GitHub Actions must confirm them before release.

## What was changed

- Connected browser login, student registration, category/location selection, complaint submission, optional JPEG/PNG upload, student complaint history/tracking, and staff status/remarks screens to the documented Spring API.
- Added the account entry flow: students self-register into the database, student and faculty have direct sign-in links, and authenticated admins can create department-scoped faculty accounts from the admin dashboard. Public registration cannot assign faculty or admin roles; passwords are BCrypt-hashed by the backend.
- Added an admin-only account directory for registered students and staff that never returns password hashes. Improved sign-out and expired-token handling, increased API wait limits for cold backend starts, and extended the default JWT lifetime to one hour.
- Added the backend routes needed by those screens: public department reference data, owner's complaint list/detail, identity-free staff lists, and guarded status updates with status-history rows.
- Limited faculty list, status-update, and image access to complaints routed to that faculty member's department; after an admin assigns a report, only its assignee can manage it. Admins can manage all complaints. Staff views never include submitter identity; assignment and status-history data identify staff actors only.
- Allowed the two default local frontend origins and comma-separated deployed origins through CORS configuration.
- Added row-level security to every application table in the schema, plus `db/enable-rls.sql` for an already-created Supabase schema. It adds no browser-role policies.
- Routed the default `Other` category to Student Affairs and added common-area locations to the repeatable seed. Existing custom category departments and non-null complaint routing are preserved; a separate repair script fills only missing complaint departments.
- Fixed the frontend/backend mismatches: registration now sends a department UUID, category and location selectors send database IDs, tracking references are accepted for image upload, staff status labels map to backend enums, and image uploads now match the backend's JPEG/PNG limit.
- Added backend integration assertions for signed-reference resolution, owner-only history, handler identity redaction, status-history updates, and student-versus-handler authorization.
- Added integration coverage for moderation authorization, the required reason, preservation of complaint ownership/history, and the student's removal notice.
- Added a GitHub Actions CI workflow to run Java 17 Maven verification and frontend JavaScript syntax checks. It is not a deployment workflow.
- Preserved the local demonstration through `?mode=mock`.
- Improved sign-in accessibility: role selection exposes its pressed state and updates it on selection; staff detail dialogs move focus inside, trap keyboard tabbing, and return focus to the opener. The faculty refresh action uses a semantic button; dashboard notifications now poll the API.

## Schema review

The schema keeps the privacy split: `complaints` contains no submitter ID; `complaint_ownership` contains the private owner join; and status, assignment, edit, comment, notification, and auth-token tables hold their corresponding audit data. UUID foreign keys connect departments, categories, buildings, floors, rooms, images, ownership, assignees, and actors. Composite foreign keys prevent pairing a classroom with the wrong floor or building. Status and image type/size checks match the backend enums and upload limits. Seed statements are repeatable.

Individual assignees and staff actors are tracked by the current code and migration. Existing records receive null actors because the old database did not store them. Student edit history is preserved in `complaint_revisions`; removal remains reversible and its reason remains auditable. The category-to-complaint department match is still set by backend code; the SQL schema does not independently enforce that pair.

## Verification and limits

- `node --check` passes for frontend JavaScript, and `npm run build` passes when `CAMPUSCARE_API_BASE_URL` is set to the backend HTTPS origin. These checks do not replace browser or production API checks.
- Backend integration tests now cover student edits/replies, student anonymity in staff views, staff actor history, faculty assignment scope, persisted revisions, and the SMTP-disabled error. They require Maven/Java 17 and have not run in this workspace because `mvn` is unavailable.
- The production API 500 remains unverified from deployment logs. The new backend maps columns and tables that must first be applied using `backend/src/main/resources/db/production-workflow-upgrade.sql`; deploying before that migration will fail Hibernate schema validation. No live Supabase migration or SMTP configuration was performed from this workspace.
- After the migration and backend deploy, still run a real production pass for health, registration, student/faculty/admin login, complaint edit and replies, assignment, removal, notifications, password reset, and photo upload. Keep credentials local; do not paste them into chat.
- Source changes are in the workspace. Push the release from the connected Git checkout only after applying the database migration; verify GitHub Actions before treating it as production-ready.

## Before using the deployed system

1. Do not rerun `schema.sql` on the populated database. Apply `db/production-workflow-upgrade.sql` in Supabase SQL Editor and confirm success before deploying the matching backend.
2. If not already applied, run the RLS, seed, and routing-repair scripts described in `DEPLOYMENT.md`.
3. Confirm the existing backend variables and deploy the backend. Configure SMTP on that backend project before setting `APP_EMAIL_ENABLED=true`; otherwise verification/reset links are unavailable and return HTTP 503.
4. Configure `CAMPUSCARE_API_BASE_URL` in the frontend Vercel project and `FRONTEND_ORIGIN` in the backend project, then deploy the frontend.
5. Provision faculty through the admin portal and test separate student, faculty, and admin accounts, including the workflows listed in Verification and limits.
