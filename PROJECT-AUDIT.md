# CampusCare project audit

Audit date: 17 November 2026

## What was changed

- Connected browser login, student registration, category/location selection, complaint submission, optional JPEG/PNG upload, student complaint history/tracking, and staff status/remarks screens to the documented Spring API.
- Added the backend routes needed by those screens: public department reference data, owner's complaint list/detail, identity-free staff lists, and guarded status updates with status-history rows.
- Limited faculty list, status-update, and image access to complaints routed to that faculty member's department. Admins can manage all complaints. Handler response DTOs do not include user IDs, names, or email addresses.
- Allowed the two default local frontend origins and comma-separated deployed origins through CORS configuration.
- Added row-level security to every application table in the schema, plus `db/enable-rls.sql` for an already-created Supabase schema. It adds no browser-role policies.
- Routed the default `Other` category to Student Affairs and added common-area locations to the repeatable seed. Existing custom category departments and non-null complaint routing are preserved; a separate repair script fills only missing complaint departments.
- Fixed the frontend/backend mismatches: registration now sends a department UUID, category and location selectors send database IDs, tracking references are accepted for image upload, staff status labels map to backend enums, and image uploads now match the backend's JPEG/PNG limit.
- Added backend integration assertions for signed-reference resolution, owner-only history, handler identity redaction, status-history updates, and student-versus-handler authorization.
- Added a GitHub Actions CI workflow to run Java 17 Maven verification and frontend JavaScript syntax checks. It is not a deployment workflow.
- Preserved the local demonstration through `?mode=mock`.
- Improved sign-in accessibility: role selection exposes its pressed state and updates it on selection; staff detail dialogs now move focus inside, trap keyboard tabbing, and return focus to the opener. The faculty refresh action uses a semantic button, and unavailable notification actions give feedback.

## Schema review

The ten-table design has the right privacy separation: `complaints` contains no submitter ID; `complaint_ownership` contains the private owner join; and `complaint_status_history` records status/remarks events. UUID foreign keys connect departments, categories, buildings, floors, rooms, images, ownership, and history. Composite foreign keys prevent pairing a classroom with the wrong floor or building. Status and image type/size checks match the backend enums and upload limits. Seed statements are repeatable.

The schema does not track an individual assignee or who changed a status. The current backend routes faculty by the category's department and keeps history without storing handler identity. This is adequate for the present workflow but is not an assignment/audit trail by staff member. The category-to-complaint department match is set by backend code; the SQL schema does not independently enforce that pair.

## Verification and limits

- `node --check` passed for all frontend JavaScript files. All eight HTML pages passed a structural parse; the UI markup fixes were included. Source-level checks do not replace a real browser session.
- Backend integration tests were expanded but could not run in this workspace: `mvn` is not on PATH, and the documented `C:\apache-maven` and JDK 17 locations are absent here. A separate JDK 27 is bundled outside the project folder, but it is not the requested Java 17 toolchain and Maven is still missing.
- Chrome headless visual review could not be completed because the browser process was denied access to its profile/crashpad in this environment. Supabase connection, Spring startup, Storage upload, CORS in a real browser, and end-to-end role behavior remain unverified because this workspace has no database credentials or deployed backend URL. Keep those secrets local; do not paste them into chat.
- The extracted project directory has no `.git` metadata, so changes are present in the working files but cannot be reported as a Git diff/commit.
- The CI workflow will start running after this folder is initialized as a Git repository and pushed to GitHub. Backend deployment still needs a hosting URL and private environment variables.

## Before using the deployed system

1. Run `db/enable-rls.sql` once in Supabase SQL Editor because the project schema was already created. Do not rerun `schema.sql` against the populated database.
2. Rerun `db/seed.sql` to add common-area classroom options and set `Other`'s department when it is currently blank. Existing custom category departments are preserved.
3. Run `db/repair-complaint-routing.sql` once to fill only complaint department values that are currently null; non-null routing is preserved.
4. Reset the previously exposed database password, set backend secrets locally, and start Spring Boot.
5. Serve the frontend at `http://localhost:5500`; test registration, login, complaint submission, student tracking, and handler status updates with separate student/faculty/admin accounts.
6. Deploy the backend, set its HTTPS URL in `frontend/js/config.js`, and set `FRONTEND_ORIGIN` to the exact Vercel origin.
7. Provision an admin account before testing handler routes. Disable bootstrap-admin credentials after first use.
