# Processor search and Brand request UI

User-approved scope: use provided Processor HTML styling to build the BRAND request flow; GET /processors/search is BRAND-only. Existing one-processor, all-capabilities, positive integer grams and seven-day locking policies apply.

- Backend: failing search integration test first, grouped all-purpose matching and common quantity bounds; upper-bound lead-time lookup; paginated results; owned material availability endpoint.
- Frontend: separate Brand page, keep Processor route, feature/API/hooks pattern, material/quantity/purpose inputs, search + select, confirm/create, success/cancel, loading/error/empty states and reset stale selections.
- Runtime: isolated localhost H2 test server with real JWT and synthetic fixtures, ignored local frontend login harness. No changes to production authentication.
- Verification: backend full regression, frontend unit/build/lint, browser desktop/mobile and actual search/create/cancel interaction. Reference file URL was blocked by browser policy; source styles are used as inspiration instead of claiming screenshot fidelity.

Completed verification: backend 318 tests passed; frontend 181 tests passed (including existing worktree tests), build and lint passed. Real local browser checks covered multi-purpose matching, weight bands, over-availability rejection, empty results, creation/lock, cancellation/unlock, UTC expiry and desktop/mobile layout. Test requests were canceled and available inventory restored. Frontend QA details are in docs/processing-design-qa.md in the frontend checkout.
