# Backend integration readiness

API wiring is present in the current frontend and backend source. The detailed changes and constraints are recorded in the repository-level [PROJECT-AUDIT.md](../PROJECT-AUDIT.md).

Java/Maven tests, a live Supabase connection, browser CORS behavior, and deployed frontend/backend requests have not been verified in this environment. Run the backend tests and local flow after configuring the database and the required signing secrets. Keep all credentials local.
