# CampusCare project audit

Audit date: 17 November 2026

## Follow-up requirements hardening

- The existing complaint/ownership split, identity-free handler DTOs, department-scoped faculty queue, student tracking, remarks history, and role checks cover the requested anonymous-to-staff workflow. This is privacy from faculty/admin application views, not anonymity from privileged database operators; students should avoid putting identifying details in report text or photos.
- Expanded the repeatable location seed to seven campus levels across Main Building, Science Building, and Sports Complex, with canteen, dining area, labs, library, gym, sports hall, courts, field, and common-area choices. Review the names against the actual campus layout and rerun `seed.sql` in Supabase; do not rerun `schema.sql` on the populated database.
- Frontend and backend now reprocess complaint photos before upload/storage to remove embedded image metadata. Added a backend integration test for EXIF removal.
- Faculty and admin status selectors now offer only transitions the backend accepts. Faculty can review prior updates and add a student-visible comment; student tracking already displays status and comment history.
- Added audited, reversible complaint moderation: department-scoped faculty can remove inappropriate or duplicate reports from their active queue with a required reason. The record, owner mapping, image metadata, and status history stay stored; the student sees the removal reason, while admins retain the audit view. Run `backend/src/main/resources/db/soft-remove-complaints.sql` in Supabase before deploying this backend update.
- Frontend JavaScript syntax checks and the Vercel frontend build passed after these changes. Backend Maven tests could not be run because Maven is not installed in this workspace. The local directory has no `.git` metadata, so these workspace changes still need to be committed/pushed through a Git checkout before Vercel can deploy them.

## What was changed

- Connected browser login, student registration, category/location selection, complaint submission, optional JPEG/PNG upload, student complaint history/tracking, and staff status/remarks screens to the documented Spring API.
- Added the account entry flow: students self-register into the database, student and faculty have direct sign-in links, and authenticated admins can create department-scoped faculty accounts from the admin dashboard. Public registration cannot assign faculty or admin roles; passwords are BCrypt-hashed by the backend.
- Added an admin-only account directory for registered students and staff that never returns password hashes. Improved sign-out and expired-token handling, increased API wait limits for cold backend starts, and extended the default JWT lifetime to one hour.
- Added the backend routes needed by those screens: public department reference data, owner's complaint list/detail, identity-free staff lists, and guarded status updates with status-history rows.
- Limited faculty list, status-update, and image access to complaints routed to that faculty member's department. Admins can manage all complaints. Handler response DTOs do not include user IDs, names, or email addresses.
- Allowed the two default local frontend origins and comma-separated deployed origins through CORS configuration.
- Added row-level security to every application table in the schema, plus `db/enable-rls.sql` for an already-created Supabase schema. It adds no browser-role policies.
- Routed the default `Other` category to Student Affairs and added common-area locations to the repeatable seed. Existing custom category departments and non-null complaint routing are preserved; a separate repair script fills only missing complaint departments.
- Fixed the frontend/backend mismatches: registration now sends a department UUID, category and location selectors send database IDs, tracking references are accepted for image upload, staff status labels map to backend enums, and image uploads now match the backend's JPEG/PNG limit.
- Added backend integration assertions for signed-reference resolution, owner-only history, handler identity redaction, status-history updates, and student-versus-handler authorization.
- Added integration coverage for moderation authorization, the required reason, preservation of complaint ownership/history, and the student's removal notice.
- Added a GitHub Actions CI workflow to run Java 17 Maven verification and frontend JavaScript syntax checks. It is not a deployment workflow.
- Preserved the local demonstration through `?mode=mock`.
- Improved sign-in accessibility: role selection exposes its pressed state and updates it on selection; staff detail dialogs now move focus inside, trap keyboard tabbing, and return focus to the opener. The faculty refresh action uses a semantic button, and unavailable notification actions give feedback.

## Schema review

The ten-table design has the right privacy separation: `complaints` contains no submitter ID; `complaint_ownership` contains the private owner join; and `complaint_status_history` records status/remarks events. UUID foreign keys connect departments, categories, buildings, floors, rooms, images, ownership, and history. Composite foreign keys prevent pairing a classroom with the wrong floor or building. Status and image type/size checks match the backend enums and upload limits. Seed statements are repeatable.

The schema does not track an individual assignee or who changed a status. The current backend routes faculty by the category's department and keeps history without storing handler identity. This is adequate for the present workflow but is not an assignment/audit trail by staff member. The category-to-complaint department match is set by backend code; the SQL schema does not independently enforce that pair.

## Verification and limits

- `node --check` passed for all frontend JavaScript files. All eight HTML pages passed a structural parse; the UI markup fixes were included. Source-level checks do not replace a real browser session.
- Backend integration tests were expanded but could not run in this workspace: `mvn` is not on PATH, and the documented `C:\apache-maven` and JDK 17 locations are absent here. A separate JDK 27 is bundled outside the project folder, but it is not the requested Java 17 toolchain and Maven is still missing.
- The original audit could not perform live checks. Subsequent checks on 17 November 2026 confirmed the deployed API health, database-backed department reads, student registration/login, and the frontend CORS preflight. Deployed Storage uploads and faculty/admin role flows still need real-account verification. Keep credentials local; do not paste them into chat.
- The extracted project directory has no `.git` metadata, so changes are present in the working files but cannot be reported as a Git diff/commit.
- The extracted project directory still has no `.git` metadata. Push these local changes through a Git checkout before Vercel can deploy them. The backend is deployed and its private environment variables are configured separately from the frontend.

## Before using the deployed system

1. Run `db/enable-rls.sql` once in Supabase SQL Editor because the project schema was already created. Do not rerun `schema.sql` against the populated database.
2. Rerun `db/seed.sql` to add common-area classroom options and set `Other`'s department when it is currently blank. Existing custom category departments are preserved.
3. Run `db/repair-complaint-routing.sql` once to fill only complaint department values that are currently null; non-null routing is preserved.
4. Reset the previously exposed database password, set backend secrets locally, and start Spring Boot.
5. Serve the frontend at `http://localhost:5500`; test registration, login, complaint submission, student tracking, and handler status updates with separate student/faculty/admin accounts.
6. Configure `CAMPUSCARE_API_BASE_URL` in the frontend Vercel project with the backend HTTPS URL and `FRONTEND_ORIGIN` in the backend Vercel project with the exact frontend origin.
7. Provision an admin account before testing handler routes. Disable bootstrap-admin credentials after first use.
