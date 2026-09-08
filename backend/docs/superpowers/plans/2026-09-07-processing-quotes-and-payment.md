# Processing Quotes and Payment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Processing request detail, quote submission/listing/selection, Job creation, payment preparation/confirmation, and 15-minute automatic payment expiry.

**Architecture:** Add JPA aggregates for quote, Job, and Job input. Keep request/quote queries separate from transactional command services; reuse the existing payment gateway at its external boundary. Every state-changing path locks its request, quote, Job, and material lock rows before checking state.

**Tech Stack:** Java 21+, Spring Boot 3.5, Spring MVC, Spring Data JPA, Bean Validation, H2 integration tests, MySQL schema and additive migration.

**Spec:** `docs/superpowers/specs/2026-09-07-processing-quotes-and-payment-design.md`

## Global Constraints

- A request identifies one Brand, one Processor, one input material, and multiple ordered process types.
- Quote price is a non-negative integer in KRW; lead days is a positive integer.
- Quote selection snapshots both active wallets, creates one Job, and attaches the existing request lock to it atomically.
- Payment expires exactly 15 minutes after Job creation; only `CREATED` Jobs expire automatically.
- Client input never controls approved amount, payment reference, actor identity, or Job fee.
- Blockchain writes remain outside this implementation.

---

### Task 1: Persistence model and schema

**Files:**
- Create: `src/main/java/com/ssafy/upcycle/processing/entity/ProcessingQuote.java`
- Create: `src/main/java/com/ssafy/upcycle/processing/entity/ProcessingQuoteStatus.java`
- Create: `src/main/java/com/ssafy/upcycle/processing/entity/ProcessingJob.java`
- Create: `src/main/java/com/ssafy/upcycle/processing/entity/ProcessingJobStatus.java`
- Create: `src/main/java/com/ssafy/upcycle/processing/entity/ProcessingInput.java`
- Create: repositories with pessimistic lock queries for quote and Job
- Modify: `ProcessingRequest` with guarded `select()`
- Modify: `schema.sql` and create additive migration `2026-09-07-processing-quotes-payment.sql`
- Test: `ProcessingQuoteApiTests`

**Interfaces:**
- `ProcessingQuote.submit(requestId, processorId, price, leadDays, memo, now)` produces `SUBMITTED`.
- `ProcessingJob.create(jobCode, request, quote, wallets, paymentMethod, now)` produces `CREATED` and `paymentExpiresAt=now+15m`.
- `ProcessingJob.markPaid(reference, amount, paidAt)` and `expire(now)` enforce state and deadline.

- [ ] Write an integration test that persists a valid quote and selected Job shape; run it and verify missing entities fail compilation.
- [ ] Add the entities, repositories, request transition, schema, and migration.
- [ ] Run the focused test and verify it passes.

### Task 2: Request detail, cancellation method, and quote APIs

**Files:**
- Create DTOs `SubmitProcessingQuoteRequest`, `ProcessingQuoteResponse`, and `ProcessingRequestDetailResponse`.
- Create `ProcessingQuoteService` and extend `ProcessingRequestQueryService`.
- Modify `ProcessingRequestController` with detail, PATCH cancel, quote POST, and quote GET routes.
- Modify `ProcessingErrorCode` with quote, Job, wallet, payment, duplicate, and expiry errors.
- Test: `ProcessingQuoteApiTests`.

**Interfaces:**
- `submit(actorId, requestId, input)` accepts only the assigned active Processor and an unexpired `QUOTING` request.
- `listQuotes(actorId, requestId)` accepts only the owning Brand.
- `detail(actorId, requestId)` accepts only the request Brand or Processor.

- [ ] Add a failing HTTP test for PATCH cancellation and party-only request detail.
- [ ] Implement the controller/query behavior and make the test pass.
- [ ] Add a failing HTTP test for quote validation, assigned-Processor submission, duplicate conflict, and Brand-only list.
- [ ] Implement quote commands and queries and make the tests pass.

### Task 3: Quote selection and Job creation

**Files:**
- Create DTOs `SelectProcessingQuoteRequest` and `ProcessingJobResponse`.
- Create `ProcessingQuoteController`, `ProcessingJobCodeGenerator`, and `ProcessingJobService`.
- Extend repositories for processing request input and material lock lookup.
- Test: `ProcessingQuoteApiTests`.

**Interfaces:**
- `select(actorId, quoteId, paymentMethod)` returns a new Job response.
- It locks the quote/request, validates Brand ownership and deadlines, snapshots active wallets, saves Job/Input, transitions request/quote, and calls `attachToProcessingJob` on the same request lock.

- [ ] Add a failing HTTP test for selection, Job fields, Processing Input, and preservation of the lock id and amount.
- [ ] Implement the minimal selection transaction and make the test pass.
- [ ] Add failing tests for wrong Brand, missing wallet, expired request, and duplicate selection.
- [ ] Implement errors and concurrency guards and make the tests pass.

### Task 4: Processing payment APIs and automatic expiry

**Files:**
- Create DTOs `ProcessingPaymentReadyResponse` and `ProcessingPaymentConfirmRequest`.
- Create `ProcessingPaymentService`, `ProcessingJobController`, `ProcessingJobQueryService`, and `ProcessingJobExpiryWorker`.
- Add Processing configuration for the expiry schedule.
- Test: `ProcessingQuoteApiTests` and a focused expiry worker test.

**Interfaces:**
- `ready(actorId, jobId)` calls `PaymentGateway.ready` from server-owned Job data and records the returned tid.
- `confirm(actorId, jobId, pgToken)` calls `PaymentGateway.approve`, compares approved amount with fee, and marks the Job `PAID`.
- `expire(jobId)` locks a due `CREATED` Job, marks it `EXPIRED`, and expires all active Job locks.

- [ ] Add a failing HTTP test for Brand-only payment preparation and server-derived amount.
- [ ] Implement payment preparation and make the test pass.
- [ ] Add a failing HTTP test for confirmation, amount mismatch, duplicate confirmation, and late confirmation.
- [ ] Implement payment confirmation and make the tests pass.
- [ ] Add a failing test for 15-minute expiry and lock expiry, including a `PAID` control case.
- [ ] Implement the bounded scheduled worker and make the test pass.
- [ ] Add a failing party-only Job detail test, implement the query, and make it pass.

### Task 5: Verification

**Files:**
- Modify API documentation if response examples differ from the design.

- [ ] Run `./mvnw.cmd test` with JDK 21+ and verify zero failures/errors.
- [ ] Run `git diff --check` and inspect the final status so unrelated untracked files remain excluded.
- [ ] Review endpoint contracts against the design and record any blockchain integration boundary left for its owner.
