# CampusCare — College Complaint Management System

CampusCare is a static web frontend backed by a Java 17 / Spring Boot REST API and Supabase PostgreSQL. The browser calls only the backend; database credentials, Supabase Storage keys, and signing secrets stay on the backend.

## Project folders

- `backend/` — Spring Boot API, database schema and seed SQL, and Maven integration tests.
- `frontend/` — HTML/CSS/JavaScript client and its API contract documentation.
- `.github/workflows/ci.yml` — Java 17 Maven verification and frontend JavaScript syntax checks on GitHub Actions.
- `PROJECT-AUDIT.md` — audit findings, fixes, and verification limits.
- `DEPLOYMENT.md` — Supabase setup, API container hosting, and Vercel frontend deployment.

## Student registration

Student registration collects the student's stream and semester in addition to their name, email, and department. Supported streams are MSS, MCS, PGDM, and MBA; semesters are 1 or 2. The admin account directory displays the total student count and each student's stream and semester. Existing accounts remain valid with blank academic fields.

For an existing Supabase database, run the latest `backend/src/main/resources/db/production-workflow-upgrade.sql` before deploying the academic-profile release.

## Database setup

For a new Supabase project, run these files in Supabase SQL Editor in order:

1. `backend/src/main/resources/db/schema.sql`
2. `backend/src/main/resources/db/seed.sql`

The repeatable seed provides seven campus levels across Main Building, Science Building, and Sports Complex. It includes a canteen, dining area, labs, library, gym, sports hall, courts, sports ground, and common-area choices. Adjust the seed names to match the college's actual floor plan before applying it.

For the existing database described in the project brief, do not rerun `schema.sql`. Run `enable-rls.sql` once, rerun the idempotent `seed.sql` to add common-area locations, then run `repair-complaint-routing.sql` once if existing complaints have a null `department_id`. The repair only fills null routing and preserves non-null values.

## Local development

Configure the backend variables listed in [backend/README.md](backend/README.md) in a local PowerShell session. The database URL is JDBC format and contains no password because the user and password are separate variables. Reset the previously exposed database password first. Never paste database passwords or secret keys into chat or frontend files.

In one PowerShell window:

```powershell
Set-Location .\backend
mvn spring-boot:run
```

In a second PowerShell window from the project root:

```powershell
python -m http.server 5500 --directory .\frontend
```

Open `http://localhost:5500`. API mode is the default and targets `http://localhost:8080`. Append `?mode=mock` to a page for the tab-scoped browser preview.

Run backend tests with `mvn test`; build and package with `mvn package`. GitHub Actions runs `mvn verify` and frontend syntax checks. The frontend and backend Vercel projects are connected to this repository and deploy from the production branch.

## Privacy and workflow boundaries

Complaint ownership is stored separately from complaint content. Student views require ownership; faculty views and updates are restricted to the department routed by complaint category; admins can see all reports. Handler API DTOs omit submitter identity. This is not absolute anonymity: authentication records and privileged backend access can still identify requesters.

The current schema does not assign reports to individual staff members or record the staff member who changed a status. Faculty routing uses the selected category's department. See the backend README and frontend API integration plan for endpoint details.
