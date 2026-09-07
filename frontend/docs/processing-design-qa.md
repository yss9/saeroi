# Brand processing request verification

Verified 2026-09-07 against the local Vite app and an isolated H2 backend with real JWT authentication and synthetic records.

The supplied Processor HTML files informed the teal palette, sidebar, cards and typography. The new flow adapts those styles for Brand users; it does not reproduce the Processor screens. Reference HTML source was inspected; direct reference-file browser access was blocked, so no screenshot-fidelity claim is made.

## Browser checks

- Desktop and 390 × 844 mobile: readable form, responsive layout, no horizontal overflow.
- Available material: 30,000 g balance minus 5,000 g active reservation = 25,000 g.
- 26,000 g input is rejected; multiple selected purposes return only matching processors.
- 10,000 g returns three processors and the matching weight-band durations. 20,000 g excludes the processor with a 10,000 g maximum and shows the higher-band durations.
- Dyeing returns an explicit empty result and guidance to relax conditions.
- Select one processor, review, and create a real request: active reservation increases without reducing token balance.
- Cancel the request: available quantity returns to 25,000 g. Test requests were canceled and the form reset for further testing.
- Creation success retains the submitted 20,000 g after inventory refresh. UTC expiry is displayed in local time exactly seven days after creation.
- No browser console errors observed.

## Automated checks

- Frontend: 181 tests passed across 19 files, including nine request-flow tests. The configured full run also discovers existing landing-page worktree tests.
- Frontend build and lint passed.
- Backend: 318 tests passed, including 19 processing integration tests.

Open http://localhost:5173/processing-preview.html while the local servers are running. The ignored login harness contains a temporary synthetic-user token. Production DB, quote confirmation, and onchain transactions were not part of this test.
