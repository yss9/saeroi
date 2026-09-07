# Processing Request Implementation Plan

**Goal:** Create a validated processing request and reserve its material atomically for seven days.

**Architecture:** JPA entities/repositories under processing, a transaction service, and a JWT-authenticated controller. Existing material locks represent reservations; the balance row serializes competing requests.

**Tech Stack:** Java 21, Spring Boot 3.5, JPA, MySQL, H2, JUnit, MockMvc.

**Spec:** ../specs/2026-09-07-processing-request-design.md

## Tasks
- [x] Write HTTP integration tests in processing/ProcessingRequestTests.java. Use real users, material balance and capability fixtures. Confirm the new POST route returns 404 before implementation.
- [x] Add request/input/type and processor capability entities and repositories; DTO validates positive integer JSON quantities without truncation. Implement creation transaction and explicit processing error codes.
- [x] Add balance and active-lock pessimistic queries. Test two simultaneous 700g requests against 1000g: exactly one succeeds, total ACTIVE reservation is 700g, balance remains 1000g.
- [x] Add cancellation/rejection and bounded seven-day expiry service, and guarded request-to-job lock attachment. Verify release/repeat/unauthorized/expiry behavior and no changes to balance.
- [x] Update schema.sql, additive migration, ERD and API documentation to match confirmed policies.
- [x] Run targeted tests, then complete Maven test suite; review diff and unresolved risks. Leave implementation in workspace for user review.

## Verification result
233 tests passed, zero failures/errors/skips. Includes 13 Processing integration tests, actual concurrent reservations, forced persistence rollback, and lifecycle cases. Reviewed independently; both findings resolved. Production MySQL load testing and applying the migration remain deployment tasks.
