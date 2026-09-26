# Processing Job Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the paid Processing Job lifecycle through signed result approval and processed child Material issuance.

**Architecture:** Keep state rules in entities and one command service transaction per transition. Store replay-safe processing signature challenges separately from login nonces, and perform approval-side lock consumption, balance updates, child creation, lineage, and events atomically.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Security JWT, Spring Data JPA, MySQL 8, JUnit 5, Mockito.

**Spec:** `docs/superpowers/specs/2026-09-11-processing-job-workflow-design.md`

## Global Constraints

- Quantities are positive integer grams.
- Result conservation is `sum(output quantities) + loss = input quantity`.
- At least one active Processing Job evidence file is required before result submission.
- MetaMask signatures use `personal_sign` and one-time, five-minute action challenges.
- Actual blockchain transaction submission is outside this implementation.

---

### Task 1: Processing Job state domain and persistence

**Files:** modify `ProcessingJob`, `schema.sql`; create shipping/output/signature entities, repositories, DTOs; test domain transitions and challenge expiry/use.

- [ ] Write failing entity tests for every valid and invalid state transition, draft revision, result conservation, and one-time challenge use.
- [ ] Run the focused tests and confirm failures are caused by missing behavior.
- [ ] Implement entity state methods and schema-aligned persistence models.
- [ ] Run focused tests and keep them green.

### Task 2: Job commands and signed result workflow

**Files:** modify `ProcessingJobController`, `ProcessingJobService`, `ProcessingErrorCode`; create command/query/signature services and request/response DTOs; test services and controllers.

- [ ] Write failing tests for party authorization, status gates, result draft replacement, evidence requirement, challenge binding, signature verification, rejection, and resubmission.
- [ ] Run the focused tests and confirm expected failures.
- [ ] Implement list/detail, ship/receive/start/results/challenge/submit/reject commands.
- [ ] Run focused tests and refactor only after green.

### Task 3: Approval material transaction and evidence authorization

**Files:** create approval service; modify Material factory/event types, Evidence validator/access policy, repositories, and schema; add integration-style service tests.

- [ ] Write failing tests proving approval consumes one lock and balance, creates all child Materials, links outputs and lineage, records events, and rolls back on invalid data.
- [ ] Run tests to prove the behavior is absent.
- [ ] Implement the approval transaction and Processing Job evidence ownership rules.
- [ ] Run material, evidence, and processing tests.

### Task 4: Frontend Job list/detail/work flow

**Files:** add Processing Job API/types/hooks/pages/components/routes in the frontend and reuse the existing `/processor/profile` visual system.

- [ ] Write failing component/API tests for role-specific actions and MetaMask challenge signing.
- [ ] Implement list, detail, shipping, receiving, starting, result draft/evidence, submission, approval, and rejection UI.
- [ ] Run frontend tests and production build.

### Task 5: Full verification

- [ ] Run backend focused tests, then `mvnw.cmd test`.
- [ ] Validate `schema.sql` mappings through application context tests.
- [ ] Run frontend test suite and build.
- [ ] Review the final diff against every API and state transition in the design.
