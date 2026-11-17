# CampusCare frontend testing report

> Historical report for the frontend-only preview as of 14 November 2026. It predates the API integration. Current changes and verification limits are in the repository-level [PROJECT-AUDIT.md](../PROJECT-AUDIT.md).

Date: 2026-11-14

Testing used Chrome headless with Chrome DevTools mobile metrics. No backend or Supabase connection was used. Google Fonts requests were blocked during the browser run, so the pages used their local font fallbacks.

## Page and responsive checks

All eight pages were opened individually at all five requested widths (40 page/viewport combinations). Each page reached `document.readyState === "complete"`; local stylesheets loaded; no image with a source URL failed; and `document.documentElement.scrollWidth` equaled the emulated viewport width.

| Page | 1440 | 1024 | 768 | 390 | 360 |
|---|---|---|---|---|---|
| `index.html` | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** |
| `login.html` | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** |
| `register.html` | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** |
| `student-dashboard.html` | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** |
| `faculty-dashboard.html` | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** |
| `admin-dashboard.html` | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** |
| `submit-complaint.html` | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** |
| `complaint-status.html` | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** |

| Responsive or runtime check | Result | Details |
|---|---|---|
| Viewport metadata | **PASS** | All eight documents include `width=device-width, initial-scale=1`. |
| Page-level horizontal overflow | **PASS** | Zero of 40 page/width combinations had document-level horizontal overflow. No global `overflow-x:hidden` is used to mask it. |
| Local CSS and image loading | **PASS** | No missing local stylesheets or broken images with a `src` were detected. The CSS campus artwork needs no image asset. |
| JavaScript runtime/console | **PASS** | Zero `Runtime.exceptionThrown` events and zero console error calls during the sweep and exercised interactions. |
| Mobile header and dashboard navigation | **PASS** | Mobile menu opened and closed; dashboard sidebar opened/closed and updated `aria-expanded`. |
| Visual review | **PASS** | Inspected Chrome captures at 1440px and 390px for the home page, plus 390px admin and complaint form captures. |

## Functionality checks

| Functionality | Result | What was exercised |
|---|---|---|
| Login preview | **PASS** | Selected Faculty role, toggled password visibility, submitted valid preview fields and confirmed the demo session/notice. |
| Registration preview | **PASS** | Mismatched passwords were rejected; matching values showed the preview-only registration message. No account was created. |
| Student dashboard and ownership | **PASS** | Complaint rows and tracking link rendered. Added a test record owned by a different student and confirmed it was excluded from the current student's list. |
| Complaint validation and submission | **PASS** | Empty required fields were blocked; a completed form produced and stored a mock complaint reference and location. |
| Complaint tracking | **PASS** | Opened the submitted reference and confirmed its status, location, image and remarks area were visible. |
| Logout | **PASS** | Logout cleared the preview session. |
| Faculty complaint view | **PASS** | Complaint cards, location, image, detail dialog, status change and remarks save were exercised. |
| Admin dashboard | **PASS** | Statistics rendered; search, status and building filters worked; status and remarks updates persisted in preview storage. |
| Location dropdowns | **PASS** | Building enabled floors; floor enabled rooms; changing the floor reset the room; changing the building reset dependent fields; Other / Common Area was accepted. |
| Image upload | **PASS** | Synthetic JPG, JPEG, PNG and WEBP files previewed; invalid type and a file over 5 MB were rejected; remove worked; a preview image appeared in the complaint and staff views. |
| Staff-view identity redaction | **PASS** | Added a test complaint containing a student name, email and registration number; these fields did not appear in faculty/admin lists or details. This verifies frontend rendering only. |

Department, floor and room filters were not individually exercised; status, search and building filters were. Multiple-image upload is not implemented; the existing UI accepts one image.

## Bugs fixed

- **Viewport test setup:** the prior `--window-size=390` screenshot was 390px wide while Chrome's CSS viewport remained 500px. `--window-size` alone had resized the capture, not emulated a mobile viewport. The verification now uses `Emulation.setDeviceMetricsOverride` with `mobile: true`; it reported exact 390px and 360px CSS viewports.
- Removed the global horizontal-overflow hiding rule and the 560px table minimum that overrode the small-screen table layout.
- Converted the admin table to responsive complaint cards below 1150px, where the fixed sidebar leaves insufficient room for its six columns. Student tables also switch to card-style rows on mobile.
- Scoped hero sizing and wrapping so the desktop headline does not split “responsibility” mid-word; preserved the intentional mobile line break.
- Kept the dashboard sidebar off-canvas on mobile and synchronized its `aria-expanded` state.
- Replaced the `javascript:` back link with a button and shared handler.
- Restricted rendered complaint image sources to data-image URLs from the supported image formats. Complaint text is HTML-escaped before template insertion.

## Failed and untested checks

- **FAIL (initial test method):** the old 390px screenshot method did not set a 390px CSS viewport. This was a test-harness failure, not a page viewport-meta failure; the exact-width sweep above passed after switching to device-metrics emulation.
- **NOT TESTED:** physical Android/iPhone devices, keyboard-only navigation and screen-reader behavior.
- **NOT TESTED:** external Google Fonts availability; requests were blocked for deterministic local browser testing and fallback fonts were used.
- **NOT TESTED:** every individual page link/CTA was not clicked end-to-end, although all pages were opened directly and the mobile menu toggles were exercised.
- **NOT TESTED:** department, floor and classroom admin filters; image upload progress; multi-image handling.
- **NOT TESTED:** real authentication, server persistence, backend access control or API responses; these are not connected.

## Privacy and backend requirements

The browser test confirms staff-facing frontend templates omitted even deliberately injected identity fields. This is not an anonymity or authorization guarantee. The Java backend must enforce role permissions, student ownership, faculty assignment rules, and identity redaction in every staff-facing response. Login, registration, report creation, image retention, status changes and remarks remain browser-local previews until the backend contract is supplied.
