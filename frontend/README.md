# CampusCare frontend

Static HTML, CSS, and JavaScript frontend. API mode is the default and talks to the Spring Boot backend. Add `?mode=mock` to a page to use the browser-only demonstration; the setting stays active for the current tab.

## Run locally

From the project folder, start the backend in one PowerShell window, then serve the frontend in another:

```powershell
Set-Location .\backend
mvn spring-boot:run
```

```powershell
Set-Location .
python -m http.server 5500 --directory .\frontend
```

Open `http://localhost:5500`. Do not open the HTML files directly with `file://`; browsers block the API request origin. The backend allows localhost and 127.0.0.1 on port 5500 by default.

## API configuration

Local API base URL is `http://localhost:8080`. Set the backend's `FRONTEND_ORIGIN` to the exact deployed frontend origin, for example `https://your-site.vercel.app`.

For Vercel, set `CAMPUSCARE_API_BASE_URL` in the frontend project's environment variables. The build validates the HTTPS URL and creates a clean `dist` folder containing only the HTML pages and their CSS/JavaScript assets. Vercel serves that folder; local notes and build scripts are excluded.

The browser sends the signed JWT in the `Authorization` header and stores it in tab-scoped `sessionStorage`. Never put Supabase database credentials, service-role keys, or signing keys in frontend code or the frontend Vercel project; configure those only as backend project environment variables.

Students register at `register.html`; the backend saves their account in PostgreSQL and assigns the `STUDENT` role. Faculty sign in at `login.html?role=faculty`. An administrator creates faculty accounts in the protected admin dashboard and assigns each account to a department. There is no public faculty signup, so visitors cannot grant themselves staff access. The administrator should share the initial password with the faculty member through a private channel.

See [API-INTEGRATION-PLAN.md](API-INTEGRATION-PLAN.md) for the endpoint mapping and remaining deployment settings, and [PROJECT-AUDIT.md](PROJECT-AUDIT.md) for the source audit and verification status.
