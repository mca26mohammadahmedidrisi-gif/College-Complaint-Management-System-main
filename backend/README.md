# College Complaint Management Backend

Java 17 / Spring Boot 3.5 REST API backend. The frontend is developed and hosted separately.

## Database setup

The database is Supabase PostgreSQL. Initialize a new project in Supabase SQL Editor by running these files in order:

1. `src/main/resources/db/schema.sql`
2. `src/main/resources/db/seed.sql`

For a project where the schema was already created, do not rerun `schema.sql`. Run `src/main/resources/db/enable-rls.sql` once, then rerun the idempotent `seed.sql`. If existing complaints have a null `department_id`, run `src/main/resources/db/repair-complaint-routing.sql`; it fills only null values and preserves any existing non-null routing. Before deploying the moderation code, run `src/main/resources/db/soft-remove-complaints.sql` once; it adds nullable audit columns and is safe to rerun. No browser-facing RLS policies are created; application access goes through the backend.

`schema.sql` creates the tables and indexes and is intended for a new/empty schema. It is not a migration and is not safe to run twice without planning a migration. The seed file can be rerun; it uses unique keys to avoid duplicate reference rows. It adds seven configured campus levels across Main Building, Science Building, and Sports Complex, including canteen, labs, library, gym, courts, sports ground, and common-area locations. Review those names against the actual campus layout before running it. Hibernate is configured to validate mapped tables and never create or alter the database schema.

Set connection variables in the local shell or backend hosting environment. Do not commit passwords, `.env` files, or Supabase service-role keys.

```text
SUPABASE_DB_URL=jdbc:postgresql://<host>:<port>/postgres?sslmode=require
SUPABASE_DB_USER=<database-user>
SUPABASE_DB_PASSWORD=<database-password>
SUPABASE_URL=https://<project-ref>.supabase.co
SUPABASE_STORAGE_BUCKET=complaint-images
SUPABASE_SERVICE_ROLE_KEY=<server-only-service-role-key>
SUPABASE_SIGNED_URL_TTL_SECONDS=300
FRONTEND_ORIGIN=http://localhost:5500,http://127.0.0.1:5500,http://localhost:3000
PORT=8080
```

`SUPABASE_DB_URL` contains only the JDBC host, port, database, and SSL option. Keep the username and password in their separate environment variables; do not put the password in the URL or URL-encode it when using these separate variables. Copy the host, port, and username exactly from the Supabase Connect dialog. Use the session pooler on port 5432 when the local network cannot reach the direct IPv6 endpoint.

### Create the private image bucket

In the Supabase Dashboard, open **Storage**, choose **New bucket**, name it `complaint-images`, and leave **Public bucket** turned off. Set the file size limit to 5 MB and, if the dashboard offers allowed MIME types, allow only `image/jpeg` and `image/png`. You can also create or reassert the private bucket in Supabase SQL Editor with:

```sql
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('complaint-images', 'complaint-images', false, 5242880,
        ARRAY['image/jpeg', 'image/png']::text[])
ON CONFLICT (id) DO UPDATE
SET name = EXCLUDED.name,
    public = false,
    file_size_limit = EXCLUDED.file_size_limit,
    allowed_mime_types = EXCLUDED.allowed_mime_types;
```

The application does not create or make the bucket public automatically. The current `complaint_images` table constraint accepts JPEG and PNG only, so WebP is intentionally rejected until a separately approved database migration changes that constraint.

Set `SUPABASE_URL` to the project URL (for example `https://<project-ref>.supabase.co`), `SUPABASE_STORAGE_BUCKET=complaint-images`, and `SUPABASE_SERVICE_ROLE_KEY` to a server-side elevated key. The value can be the current `sb_secret_...` API key or the legacy JWT-based `service_role` key. The backend sends a new `sb_secret_...` key only in the `apikey` header because it is not a JWT; legacy service-role keys continue to be sent in both `apikey` and `Authorization: Bearer` headers. In Supabase, obtain the key from **Project Settings → API Keys**. Store it only in the backend deployment's secret environment configuration; never put it in source code, frontend settings, or client responses. `SUPABASE_SIGNED_URL_TTL_SECONDS` controls temporary image links and defaults to 300 seconds (allowed range: 60–3600).

## Authentication and roles

Authentication uses Spring Security's stateless bearer-token support. Passwords are hashed with BCrypt. The JWT is signed using HS256; the signing key must be supplied as base64 text that decodes to at least 32 random bytes. Generate one with:

```bash
openssl rand -base64 32
```

Configure:

```text
JWT_SECRET_BASE64=<generated-base64-secret>
JWT_ISSUER=college-complaint-management
JWT_EXPIRATION_SECONDS=3600
```

Complaint tracking references use a separate HMAC signing key. Generate an independent key using the same command and configure it as `COMPLAINT_TRACKING_SECRET_BASE64`:

```text
COMPLAINT_TRACKING_SECRET_BASE64=<another-generated-base64-secret>
```

Do not reuse the JWT key. Rotating this key invalidates previously issued tracking references; arrange rotation as a planned migration if references must remain usable.

Never commit the signing key or expose it to the frontend. The default token lifetime is 1 hour; set the backend Vercel variable `JWT_EXPIRATION_SECONDS` to `3600` as well if it already overrides the default. JWTs contain a user UUID subject and role claim, but no email or name. Spring Security validates the signature, issuer, and expiration on protected API requests. Keep `JWT_SECRET_BASE64` identical across backend instances and deployments so tokens remain valid consistently.

Public registration creates students only. An admin can create faculty or additional admin accounts through `POST /api/admin/users`. Student creation through this route is rejected. Faculty must include an existing `departmentId`; admin department is optional.

`GET /api/admin/users` returns the admin-only account directory with name, email, role, department, active state, and registration time. It never returns password hashes. The admin dashboard uses this directory to show enrolled students and existing staff.

In the frontend, student registration is public and writes a student account to the `users` table with a BCrypt password hash. Faculty use the faculty sign-in link, but their accounts must first be created by an authenticated admin in the admin dashboard. That flow posts the faculty name, email, department, and initial password to the admin-only endpoint. The backend enforces the role and department assignment; do not add public faculty self-registration. The first admin can be provisioned once through protected deployment environment variables:

```text
APP_BOOTSTRAP_ADMIN_ENABLED=true
BOOTSTRAP_ADMIN_FULL_NAME=College Administrator
BOOTSTRAP_ADMIN_EMAIL=admin@example.edu
BOOTSTRAP_ADMIN_PASSWORD=<strong-one-time-password>
```

On startup, the bootstrap creates an administrator only if no admin exists. After successful provisioning, remove those values or set `APP_BOOTSTRAP_ADMIN_ENABLED=false`; do not leave the bootstrap password in deployment configuration. The bootstrap password is hashed before database storage.

## API endpoints

All responses use `success`, `message`, `data`, and `timestamp` fields. Errors use the same envelope. Validation errors put field messages in `data`.

### Register a student

`POST /api/auth/register` — public; always assigns `STUDENT`.

Request:

```json
{
  "fullName": "Rahul Sharma",
  "email": "rahul@example.com",
  "password": "a-strong-password",
  "departmentId": "b9b8c000-0d32-4a1e-b364-f17b667d28bc"
}
```

Success (`201 Created`):

```json
{
  "success": true,
  "message": "Student registered successfully",
  "data": {
    "userId": "e21cfc30-08d6-4acf-a594-9d55e68e5aec",
    "user": {
      "fullName": "Rahul Sharma",
      "email": "rahul@example.com",
      "role": "STUDENT"
    }
  },
  "timestamp": "2026-11-15T10:00:00Z"
}
```

Validation failures return `400 Bad Request`; duplicate emails return `409 Conflict`.

### Log in

`POST /api/auth/login` — public; works for student, faculty, and admin accounts.

Request:

```json
{
  "email": "rahul@example.com",
  "password": "a-strong-password"
}
```

Success (`200 OK`):

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "token": "<signed-jwt>",
    "tokenType": "Bearer",
    "expiresInSeconds": 3600,
    "role": "STUDENT",
    "user": {
      "fullName": "Rahul Sharma",
      "email": "rahul@example.com",
      "role": "STUDENT"
    }
  },
  "timestamp": "2026-11-15T10:00:00Z"
}
```

Invalid credentials return `401 Unauthorized` with the same message for an unknown email and a wrong password. Invalid request fields return `400 Bad Request`.

Send the token on protected requests:

```text
Authorization: Bearer <signed-jwt>
```

### Log out

`POST /api/auth/logout` — requires a valid bearer token. Returns `200 OK` with `data.strategy` set to `client_discard_until_expiration`.

This phase does not keep a token revocation list, so logout does not invalidate a copied token. The client must discard it; a token remains usable until its configured expiration. Server-side revocation would require a later revocation store or short-lived access tokens plus refresh-token rotation.

### Create faculty or admin account

`POST /api/admin/users` — requires `ADMIN` role. Student creation through this route is rejected. Faculty must include an existing `departmentId`; admin department is optional.

```json
{
  "fullName": "Faculty Member",
  "email": "faculty@example.edu",
  "password": "a-strong-password",
  "departmentId": "b9b8c000-0d32-4a1e-b364-f17b667d28bc",
  "role": "FACULTY"
}
```

Returns `201 Created`. Requests without a token return `401`; authenticated non-admin users receive `403`.

## Location APIs

These read-only endpoints require a valid bearer token. Students, faculty, and admins can use all three. The current schema has no active-status columns for buildings, floors, or classrooms, so the APIs return all stored locations.

### List buildings

`GET /api/locations/buildings`

Example response (`200 OK`):

```json
{
  "success": true,
  "message": "Buildings retrieved successfully",
  "data": [
    {
      "buildingId": "d7fe844a-b8af-4d5c-8406-67f90bc0ab22",
      "buildingName": "Main Building"
    }
  ],
  "timestamp": "2026-11-15T10:00:00Z"
}
```

### List floors for one building

`GET /api/locations/buildings/{buildingId}/floors`

Example response (`200 OK`):

```json
{
  "success": true,
  "message": "Floors retrieved successfully",
  "data": [
    {
      "floorId": "29e73e98-63b8-42da-bfc4-d8f29f552ca1",
      "buildingId": "d7fe844a-b8af-4d5c-8406-67f90bc0ab22",
      "floorNumber": 0,
      "floorName": "Ground Floor"
    }
  ],
  "timestamp": "2026-11-15T10:00:00Z"
}
```

### List classrooms for one floor

`GET /api/locations/floors/{floorId}/classrooms`

Example response (`200 OK`):

```json
{
  "success": true,
  "message": "Classrooms retrieved successfully",
  "data": [
    {
      "classroomId": "012531bc-7188-48c0-b2ca-b30d1115e3a2",
      "floorId": "29e73e98-63b8-42da-bfc4-d8f29f552ca1",
      "classroomNumber": "101",
      "classroomName": "Room 101"
    }
  ],
  "timestamp": "2026-11-15T10:00:00Z"
}
```

Malformed UUIDs return `400 Bad Request`; a well-formed but missing building/floor returns `404 Not Found`; missing or invalid authentication returns `401 Unauthorized`. An empty list is returned as `200 OK` when the parent exists but has no child records. These responses contain location data only and do not join user or complaint-ownership records.

## Complaint submission

Complaint submission requires a valid bearer token. Students, faculty, and admins can submit. First use `GET /api/complaints/categories` to list active complaint categories, then provide a category and classroom when submitting.

### List active complaint categories

`GET /api/complaints/categories`

Example (`200 OK`):

```json
{
  "success": true,
  "message": "Complaint categories retrieved successfully",
  "data": [
    {
      "categoryId": "63f84b93-74a0-4a8f-989f-c6c81d665391",
      "categoryName": "Electrical"
    }
  ],
  "timestamp": "2026-11-15T10:00:00Z"
}
```

### Submit a complaint

`POST /api/complaints`

Request (`Authorization: Bearer <signed-jwt>`):

```json
{
  "title": "Broken light",
  "description": "The ceiling light in this room is not working.",
  "categoryId": "63f84b93-74a0-4a8f-989f-c6c81d665391",
  "classroomId": "012531bc-7188-48c0-b2ca-b30d1115e3a2"
}
```

Success (`201 Created`):

```json
{
  "success": true,
  "message": "Complaint submitted successfully",
  "data": {
    "trackingReference": "<opaque-signed-reference>",
    "status": "PENDING",
    "submittedAt": "2026-11-15T10:00:00Z"
  },
  "timestamp": "2026-11-15T10:00:00Z"
}
```

The API rejects blank or oversized fields with `400 Bad Request`, unknown or inactive categories and unknown classrooms with `404 Not Found`, and missing/invalid bearer tokens with `401 Unauthorized`. The classroom determines its floor and building in the database, preventing mismatched location IDs. Image upload is not part of this endpoint.

The reference is a URL-safe HMAC-SHA256 signature over the database-generated random UUID and a format version. It is not the sequential database ID and cannot be changed without invalidating its signature. Keep `COMPLAINT_TRACKING_SECRET_BASE64` private and stable. The private history endpoint validates the signature and checks `complaint_ownership` against the signed-in user.

The database writes the complaint, a separate row in `complaint_ownership`, and the initial `PENDING` history row in one transaction. The `complaints` table has no submitter user ID. The response contains only the tracking reference, status, and submission time; it omits submitter and ownership fields. Authentication and privileged backend access can still identify a requester, so this separation does not claim absolute anonymity.

## Complaint image uploads

Images are sent to the private Supabase Storage bucket; PostgreSQL stores only the storage object path, detected media type, byte size, and timestamps. The client filename is ignored. The backend re-encodes uploaded pixels before Storage upload, stripping embedded EXIF, GPS, camera, and other metadata. The backend accepts actual, decodable JPEG and PNG content up to 5 MB, verifies the bytes rather than trusting the filename or multipart media type, limits large image dimensions, and assigns a random UUID object name. Binary data is never stored in PostgreSQL.

### Upload one image

`POST /api/complaints/{complaintId}/images` requires a bearer token and a `multipart/form-data` part named `file`.

Example with `curl`:

```bash
curl -X POST "http://localhost:8080/api/complaints/<complaint-id>/images" \
  -H "Authorization: Bearer <signed-jwt>" \
  -F "file=@classroom-photo.png;type=image/png"
```

Success (`201 Created`) returns metadata only:

```json
{
  "success": true,
  "message": "Complaint image uploaded successfully",
  "data": {
    "imageId": "e21cfc30-08d6-4acf-a594-9d55e68e5aec",
    "contentType": "image/png",
    "fileSizeBytes": 48210,
    "createdAt": "2026-11-15T10:00:00Z"
  },
  "timestamp": "2026-11-15T10:00:00Z"
}
```

The endpoint rejects empty files with `400`, unsupported or invalid image content with `415`, files over 5 MB with `413`, and missing authentication with `401`. If Storage is unavailable it returns `503` and does not save metadata. If metadata persistence fails after a successful Storage upload, the backend attempts to delete the uploaded object.

### Get authorized image links

`GET /api/complaints/{complaintId}/images` requires a bearer token. Example:

```bash
curl "http://localhost:8080/api/complaints/<complaint-id>/images" \
  -H "Authorization: Bearer <signed-jwt>"
```

The response includes image IDs, detected media types, sizes, timestamps, temporary signed URLs, and their expiration times. It does not include storage paths, user IDs, names, emails, or ownership records. Only the complaint owner can upload images. The owner, an admin, or faculty routed to the complaint's department can request image links. Requests by other users return `404` to avoid confirming that another person's complaint exists.

Signed URLs are temporary bearer links: anyone who obtains one can use it until it expires. Share them only with the authorized viewer and use HTTPS in deployed environments. Bucket access remains private; the backend's service-role credential is used only in server-side calls and is never returned by the API. Owners can upload and retrieve their images. Admins and faculty in the complaint's routed department can retrieve signed image links.

Image paths may use the opaque tracking reference returned after complaint submission; the backend validates its signature before resolving it. For staff, image retrieval is limited to admins and faculty in the complaint's routed department. Upload remains owner-only.

## Complaint history and handler workflow

`GET /api/complaints/mine` returns only complaints owned by the signed-in account. `GET /api/complaints/mine/{trackingReference}` returns one of that user's reports after validating the signed reference. Both use identity-free DTOs.

`GET /api/complaints/staff` is available to faculty and admins. Admins see all complaints; faculty see only complaints routed to their department through the selected category. Submitter IDs, names, and emails are omitted from these responses.

`PUT /api/complaints/{complaintId}/status` accepts `{ "status": "IN_PROGRESS", "remarks": "Inspection scheduled" }`. The API checks the handler role and department, validates status transitions, and records each change in `complaint_status_history`. The current schema does not assign a complaint to an individual handler.

`PUT /api/complaints/{complaintId}/remove` accepts `{ "reason": "Abusive or inappropriate language" }`. Faculty can remove only complaints routed to their own department; admins can remove any complaint. This is a reversible soft removal from the active faculty queue: the complaint, owner link, images, and history remain stored, its status becomes `REJECTED`, and the reason is added to status history and shown to the student. Removed complaints cannot be edited by the normal status workflow. Admins retain an audit view of removed reports.

`GET /api/departments` returns department IDs and names for the public student-registration form. The frontend uses these IDs, as well as category and location IDs, when submitting requests; it does not send display labels as database foreign keys.

## Error examples

Invalid credentials (`401`):

```json
{
  "success": false,
  "message": "Invalid email or password",
  "data": null,
  "timestamp": "2026-11-15T10:00:00Z"
}
```

Validation error (`400`):

```json
{
  "success": false,
  "message": "Request validation failed",
  "data": {
    "email": "Email must be valid",
    "password": "Password must contain 8 to 72 characters"
  },
  "timestamp": "2026-11-15T10:00:00Z"
}
```

## Anonymity boundary

Authentication records remain in `users`; complaint content remains in `complaints`; the submitter mapping remains in `complaint_ownership`. Staff complaint APIs return identity-free DTOs and do not serialize ownership. Owner history and image checks use the mapping in the backend. Authentication and privileged database access can still identify requesters, so the system does not claim absolute anonymity.

## Tests and build

Run the authentication integration tests against an in-memory H2 database:

```bash
mvn test
```

Build the executable application jar:

```bash
mvn package
```

The tests cover student registration and login security, locations, complaint submission by all three roles, invalid submissions, authentication rejection, initial status/history and ownership persistence, active categories, response privacy, tracking-reference integrity, and faculty complaint removal authorization and audit preservation.

## Java concepts used

- `List` converts JWT role claims into Spring Security authorities.
- `Set` stores role claims in the token.
- `Map` builds the JWT's role claim data.
- `ApiResponse<T>` is a generic response wrapper; its static response factories are generic methods.
- `GenericUtility.copyToList(Collection<? extends T>)` uses a wildcard to copy compatible claim collections.
- A lambda converts role names into granted authorities.
