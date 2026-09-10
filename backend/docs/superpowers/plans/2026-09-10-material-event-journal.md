# Material Event Journal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an append-only, queryable timeline for every material-related business process.

**Architecture:** Existing domain tables remain the source of truth. A focused material event recorder writes immutable rows in the same database transaction as each state change, while a material timeline endpoint reads those rows in chronological order.

**Tech Stack:** Java 17, Spring Boot 3, Spring Data JPA, MySQL 8, H2, JUnit 5, MockMvc

**Spec:** `docs/superpowers/specs/2026-09-10-material-event-journal-design.md`

## Global Constraints

- Quantities are integer grams.
- Existing domain tables remain authoritative.
- Event rows cannot be updated or deleted through application code.
- Event insertion and the related domain mutation share one transaction.
- Existing records receive only a baseline import event.

---

### Task 1: Event persistence model

**Files:**
- Create: `src/main/java/com/ssafy/upcycle/material/entity/MaterialEvent.java`
- Create: `src/main/java/com/ssafy/upcycle/material/entity/MaterialEventType.java`
- Create: `src/main/java/com/ssafy/upcycle/material/entity/MaterialEventReferenceType.java`
- Create: `src/main/java/com/ssafy/upcycle/material/repository/MaterialEventRepository.java`
- Modify: `src/main/resources/schema.sql`
- Create: `src/main/resources/db/migrations/2026-09-10-material-event-journal.sql`
- Test: `src/test/java/com/ssafy/upcycle/material/entity/MaterialEventTests.java`
- Test: `src/test/java/com/ssafy/upcycle/material/repository/MaterialEventRepositoryTests.java`

- [ ] Write failing entity and repository tests for validation, ordering, and duplicate idempotency keys.
- [ ] Run the focused tests and verify the missing model causes failure.
- [ ] Implement the entity, enums, repository, schema, and migration.
- [ ] Run focused tests until green.

### Task 2: Recorder and timeline API

**Files:**
- Create: `src/main/java/com/ssafy/upcycle/material/service/MaterialEventRecorder.java`
- Create: `src/main/java/com/ssafy/upcycle/material/service/MaterialEventQueryService.java`
- Create: `src/main/java/com/ssafy/upcycle/material/dto/MaterialEventResponse.java`
- Modify: `src/main/java/com/ssafy/upcycle/material/controller/MaterialController.java`
- Test: `src/test/java/com/ssafy/upcycle/material/controller/MaterialEventControllerTests.java`

- [ ] Write a failing authenticated timeline API test.
- [ ] Run it and verify the endpoint is missing.
- [ ] Implement deterministic event recording and chronological bounded lookup.
- [ ] Run focused tests until green.

### Task 3: Material, blockchain, and lineage events

**Files:**
- Modify: material registration, chain-link, source-verification, and lineage services.
- Test: extend the corresponding material integration tests.

- [ ] Add failing assertions for registration, mint, verification, and parent/child events.
- [ ] Add recorder calls after each successful mutation.
- [ ] Run material tests until green.

### Task 4: Trade events

**Files:**
- Modify: listing, trade command, payment, shipment, receipt, verification, and chain-link services.
- Test: extend representative trade integration tests.

- [ ] Add failing assertions for listing, order, payment, shipment, receipt, cancellation, and on-chain events.
- [ ] Add recorder calls in the existing transactions.
- [ ] Run trade tests until green.

### Task 5: Processing events

**Files:**
- Modify: processing request, quote, job, and payment services.
- Test: extend processing request and job tests.

- [ ] Add failing assertions for request, termination, quote, selection, payment, and expiry.
- [ ] Add recorder calls in the existing transactions.
- [ ] Run processing tests until green.

### Task 6: Production and evidence events

**Files:**
- Modify: production batch, confirmation, evidence upload, and evidence deletion services.
- Test: extend production journey and evidence service tests.

- [ ] Add failing assertions for reservation, removal, cancellation, consumption, scrap creation, upload, and deletion.
- [ ] Add recorder calls in the existing transactions.
- [ ] Run production and evidence tests until green.

### Task 7: Full verification

- [ ] Run the complete Maven test suite.
- [ ] Inspect schema validation and working-tree diff.
- [ ] Fix only failures caused by this feature and rerun affected tests.
