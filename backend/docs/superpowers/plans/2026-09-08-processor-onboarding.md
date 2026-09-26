# Processor Onboarding Implementation Plan

**Goal:** Allow authenticated PROCESSOR users to register a region, per-material/process quantity ranges, and quantity-band lead times, then expose only complete registrations in Brand processor search.

**API contract:** `GET/PUT /processors/me/profile`, `GET/PUT /processors/me/capabilities`, and `GET/PUT /processors/me/lead-times`, with `/api/v1` aliases for the frontend. PUT replaces the corresponding resource and returns the complete saved value.

## Backend

1. Add integration tests for PROCESSOR authorization, missing-resource behavior, validation, replacement, ordering, cross-resource lead-time coverage, and processor-search visibility.
2. Add request/response records, repositories, entity factories/update behavior, and a transactional `ProcessorProfileService`.
3. Add a controller with both unversioned and `/api/v1` routes.
4. Require profile, capability, and lead-time records in `ProcessorSearchService` matching queries.
5. Run focused processing tests and the full backend test suite.

## Frontend

1. Add API client tests for all six operations.
2. Add a PROCESSOR-only `/processor/profile` page test covering initial load, gram inputs, row editing, and the three PUT payloads.
3. Implement typed API functions, query/mutation hook, responsive form, route, and dashboard navigation.
4. Run focused frontend tests, full tests, lint, and production build.
