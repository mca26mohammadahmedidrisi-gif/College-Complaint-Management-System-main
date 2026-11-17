# Frontend and backend contract

The static frontend now calls the Spring Boot API in API mode. Append `?mode=mock` to a page to use the browser-only preview; the selected mode is kept for the current tab. API mode is the default.

## Local setup

- Start Spring Boot on `http://localhost:8080`.
- Serve this folder at `http://localhost:5500` (or `http://127.0.0.1:5500`). Do not open the HTML files with `file://`.
- The backend allows those local origins by default. For deployment, set `FRONTEND_ORIGIN` to the exact Vercel origin and set `productionApiUrl` in `js/config.js` to the HTTPS Spring Boot URL.
- The browser sends JWTs as `Authorization: Bearer ...`; it keeps the token in `sessionStorage` for the current tab. There is no refresh-token or server-side revocation flow yet.

## Implemented operations

| Frontend operation | Backend operation | Notes |
|---|---|---|
| Student department selector | `GET /api/departments` | Public reference data for registration. |
| Student registration | `POST /api/auth/register` | Sends full name, email, password, and department UUID. |
| Sign in / sign out | `POST /api/auth/login`, `POST /api/auth/logout` | All roles use the same login route. Logout clears this browser session; the JWT remains valid until expiry. |
| Location/category selectors | `/api/locations/...`, `GET /api/complaints/categories` | Authenticated; sends database IDs, not display labels. |
| Submit, upload image, own history | `POST /api/complaints`, `POST /api/complaints/{trackingReference}/images`, `GET /api/complaints/mine...` | Uses the signed tracking reference to avoid needing a client-visible submitter or database owner ID. Images accept JPEG/PNG only, up to 5 MB. |
| Staff complaint list and updates | `GET /api/complaints/staff`, `PUT /api/complaints/{complaintId}/status` | Admin sees all; faculty sees complaints routed to their department. Response DTOs omit submitter identity. |
| Image retrieval | `GET /api/complaints/{complaintId}/images` | Owner, admin, and same-department faculty can receive temporary signed URLs. |

Status values are `PENDING`, `ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, and `REJECTED`. Staff transitions are validated by the backend and each update is recorded in `complaint_status_history`.

## Remaining deployment decisions

- Deploy the backend and set its public HTTPS URL in `productionApiUrl`.
- Set `FRONTEND_ORIGIN` on the backend host to the deployed Vercel origin. Do not use `*` for production.
- Supply backend environment secrets in the backend host only. Never add Supabase, signing, or database secrets to this frontend.
- The current database has no assignment-to-handler field. Faculty access is routed by the complaint category's department; there is no per-person assignment workflow yet.
- Password recovery and profile management are not implemented.
