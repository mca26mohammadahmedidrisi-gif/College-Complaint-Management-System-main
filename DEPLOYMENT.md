# Supabase and Vercel deployment

The frontend and Java 17 Spring Boot API can both run on Vercel as separate Vercel projects. The API uses Vercel's container runtime through `backend/Dockerfile.vercel`; Supabase remains the database and private image-storage service. Vercel currently marks its Container runtime as beta, so review its limits and billing before relying on it for production traffic. Keep database credentials and Supabase secret keys only in the backend Vercel project's environment variables.

## 1. Prepare the Supabase project

This project already has a schema and migration scripts. Do not run `schema.sql` on the existing database. In the Supabase SQL Editor, check whether the previous audit's database steps have been applied. If not, apply `backend/src/main/resources/db/enable-rls.sql` once, rerun the idempotent `seed.sql`, and apply `repair-complaint-routing.sql` once if the existing complaints have null `department_id` values. **Before deploying the current backend, run `backend/src/main/resources/db/production-workflow-upgrade.sql` in Supabase SQL Editor and wait for success. It is repeatable and adds reply, edit-history, assignment, actor-history, notification, removal, and email-token schema. Do not deploy the backend until the SQL reports success.** The repeatable seed adds seven configured campus levels and named canteen, lab, library, gym, court, field, and common-area locations; review its names against the real campus first. Review `PROJECT-AUDIT.md` before changing production data.

In Supabase **Project Settings → Database → Connect**, copy the database connection details. For a persistent container, use the direct connection when its network supports IPv6; otherwise use the shared **Session pooler**. Use its exact host, port, and username. The application uses JDBC with SSL. Set the database password separately from the JDBC URL. The application database role must be allowed to access the application schema and tables despite RLS (the application uses a server-side database connection; never put its credentials in the browser).

If a database password or service key was previously shared or committed, rotate it in Supabase before deployment.

For image uploads, create the private `complaint-images` Storage bucket with a 5 MB limit and JPEG/PNG MIME types. Set the backend's Supabase URL and server-only Storage key. Never use a service-role key in Vercel frontend variables, source control, or browser code.

## 2. Deploy the API to Vercel

Create a Vercel project from this repository with `backend` as its root directory. The included `backend/vercel.json` selects Vercel's container runtime, and `backend/Dockerfile.vercel` builds and runs the Spring Boot API. In the Vercel project settings, set `PORT` to `8080` so the container router and Spring Boot listen on the same port.

Set these variables in the backend Vercel project's environment settings:

| Variable | Value |
| --- | --- |
| `SUPABASE_DB_URL` | `jdbc:postgresql://<copied-host>:<copied-port>/postgres?sslmode=require` |
| `SUPABASE_DB_USER` | Exact user from Supabase Connect |
| `SUPABASE_DB_PASSWORD` | Database password |
| `SUPABASE_URL` | `https://<project-ref>.supabase.co` |
| `SUPABASE_STORAGE_BUCKET` | `complaint-images` |
| `SUPABASE_SERVICE_ROLE_KEY` | Server-only key from Supabase Project Settings → API Keys |
| `JWT_SECRET_BASE64` | Fresh base64 secret that decodes to at least 32 random bytes |
| `COMPLAINT_TRACKING_SECRET_BASE64` | A different fresh base64 secret of at least 32 random bytes |
| `APP_EMAIL_ENABLED` | Set to `true` only after SMTP values are configured |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | SMTP provider credentials and verified sender; keep these backend-only |
| `FRONTEND_URL` | Frontend origin used to build verification and reset links |
| `FRONTEND_ORIGIN` | Exact frontend Vercel production origin, e.g. `https://your-project.vercel.app` |

Keep bootstrap admin disabled unless deliberately provisioning the first admin; turn it off after setup. The backend Vercel URL must be HTTPS. Copy that base URL for the frontend Vercel configuration below.

## 3. Deploy the frontend to Vercel

Import the repository into a second Vercel project and set its root directory to `frontend`. Keep the Framework Preset as **Other**. The included `frontend/vercel.json` runs a small build script and serves only the HTML pages and `css`/`js` assets from `dist`; deployment notes and build scripts are not published. Add `CAMPUSCARE_API_BASE_URL` to this project's **Production** environment variables with the backend Vercel project's HTTPS URL (no trailing slash, path, or secret). Add it to Preview as well if preview builds should call a backend. Redeploy after setting it. The build intentionally fails if this value is missing or not HTTPS.

The generated `frontend/dist/js/deployment-config.js` contains only the public API URL. Database credentials, JWT keys, and Storage keys must never be configured in Vercel's frontend project.

## 4. Verify the deployment

Check `https://<backend-project>.vercel.app/actuator/health`, then open the frontend Vercel URL. Register a student, verify the email when SMTP is enabled, submit a report, edit it while pending, and test student/faculty replies. As admin, assign it to a same-department faculty account; confirm status history actors, notifications, removal audit, and password reset. Also verify faculty department scoping with separate accounts. The frontend and API must use HTTPS and `FRONTEND_ORIGIN` must contain the exact frontend Vercel origin. Keep Supabase secrets out of chat and source control.

## Local configuration

The local static preview works with `?mode=mock`. To use the API locally, keep the frontend on `http://localhost:5500`, start Spring on port 8080, and set the required database and signing variables in the backend process environment as described in `backend/README.md`.
