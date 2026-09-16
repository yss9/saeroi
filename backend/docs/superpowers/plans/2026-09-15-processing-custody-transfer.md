# Processing Custody Transfer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transfer locked Processing input tokens from Brand to Processor at confirmed receipt, then finalize return through Brand MetaMask confirmation and Child Material issuance.

**Architecture:** `ProcessingRegistry` performs every token-changing operation atomically. The frontend sends the transaction through MetaMask and waits for its receipt; the backend then stores an idempotent DB projection of that confirmed chain result. Physical shipment remains an off-chain state transition.

**Tech Stack:** Solidity 0.8.24, Hardhat, ethers v6, Spring Boot 3/JPA/MySQL, React 19, TanStack Query, Vitest.

---

## File map

### Blockchain

- Modify `S15P21E104_blockchain/contracts/ProcessingRegistry.sol`: receipt custody transfer, replacement input locks, return shipment and finalization states/events.
- Modify `S15P21E104_blockchain/test/WorkflowIntegration.ts`: contract-level custody and replay tests.
- Regenerate `S15P21E104_blockchain/abi/ProcessingRegistry.json`.

### Backend

- Modify `src/main/resources/schema.sql` and add a dated migration: chain linkage, receipt/return transaction fields and return shipment fields.
- Modify `processing/entity/ProcessingJob.java`, `ProcessingJobStatus.java`, and `ProcessingInput.java`: new fields and guarded state transitions.
- Add Processing chain receipt DTOs under `processing/dto`.
- Add a focused `ProcessingChainService`: transaction linkage and DB balance/lock projections.
- Modify `ProcessingJobWorkflowService`: physical return shipment and final receipt finalization.
- Modify `ProcessingJobController`: canonical unversioned endpoints.
- Extend `ChainTransactionRecorder.Contract` with `PROCESSING_REGISTRY`.
- Modify Processing response DTOs and integration tests.

### Frontend

- Regenerate `src/config/abi/processingRegistry.abi.ts` from the blockchain artifact.
- Add `features/processing/services/processingContractService.ts`: typed ProcessingRegistry calls and receipt event extraction.
- Add a Processing chain hook using `useBlockchainTransaction`.
- Modify `processingJobApi.ts`: unversioned paths and chain receipt payloads.
- Modify `useProcessingJobs.ts` and `ProcessingJobDetail.tsx`: MetaMask-first receipt and return actions.
- Add/update component, API, hook, and ABI tests.

## Task 1: Contract transfers input custody at Processor receipt

- [ ] Add a failing Hardhat test proving `confirmReceipt` decreases Brand input balance, increases Processor input balance, consumes the Brand lock, and creates a Processor-owned replacement lock.
- [ ] Run the focused test and confirm it fails because current `confirmReceipt` changes only status.
- [ ] Extend the MaterialToken interface and Processing input/event definitions with replacement lock support.
- [ ] Implement receipt transfer-and-relock as one ProcessingRegistry transaction.
- [ ] Add role, wrong-state, and replay assertions.
- [ ] Run the focused contract tests.

## Task 2: Contract models return shipment and final Child output ownership

- [ ] Add failing tests for `RESULT_SUBMITTED -> RETURN_SHIPPED -> CONFIRMED`.
- [ ] Prove rejection is allowed before return shipment and blocked afterwards.
- [ ] Add `markReturnShipped` and its event.
- [ ] Make final approval consume Processor-owned input locks and mint Child Tokens to Brand.
- [ ] Assert Loss, output balances, parent linkage, caller authorization, and replay behavior.
- [ ] Run all Hardhat tests.
- [ ] Export the ProcessingRegistry ABI.

## Task 3: Backend schema and entities represent chain custody

- [ ] Add a failing JPA/schema validation test for the new Processing Job columns and `RETURN_SHIPPED` status.
- [ ] Update `schema.sql` and add a forward migration.
- [ ] Add chain Job ID, receipt transaction, and return shipment fields to `ProcessingJob`.
- [ ] Add guarded entity transitions and an input method that replaces its lock reference.
- [ ] Run focused entity/repository tests.

## Task 4: Backend projects Processor receipt idempotently

- [ ] Add a failing MVC integration test for `PATCH /processing-jobs/{jobId}/receive` with confirmed transaction data.
- [ ] Assert Brand balance decreases, Processor balance increases, Brand lock is consumed, replacement Processor lock is active, and the input points to it.
- [ ] Assert Processor ownership/role, wallet, status, amount, same-receipt retry, and conflicting-receipt rejection.
- [ ] Implement receipt DTO validation and `ProcessingChainService.receive`.
- [ ] Record `confirmReceipt` in `blockchain_transaction` with `PROCESSING_REGISTRY`.
- [ ] Run focused backend tests.

## Task 5: Backend records return shipment and final receipt

- [ ] Add failing integration tests for `PATCH /processing-jobs/{jobId}/return/ship` and `POST /processing-jobs/{jobId}/return/receive`.
- [ ] Assert rejection is blocked after return shipment.
- [ ] Assert final receipt consumes Processor balance/lock, creates Child Materials for Brand, calculates Loss, connects outputs and lineage, records Material Events, stores transaction data, and removes active custody.
- [ ] Implement return transitions and finalization in one database transaction.
- [ ] Preserve identical-receipt retry behavior and reject a conflicting hash/token mapping.
- [ ] Run all backend tests.

## Task 6: Frontend ProcessingRegistry service and unversioned API

- [ ] Add failing ABI/service tests for `confirmReceipt`, `markReturnShipped`, `approveProcessingResult`, replacement lock extraction, and output token extraction.
- [ ] Implement the Processing contract service using the shared contract factory.
- [ ] Update Processing API calls introduced or changed here to `/processing-jobs/...` without `/v1`.
- [ ] Add API tests for exact method, path, and body.
- [ ] Run focused frontend tests.

## Task 7: Frontend MetaMask-first receipt and return UI

- [ ] Add failing hook/component tests proving backend projection is called only after `tx.wait()` succeeds.
- [ ] Add Processor receipt action with signing, pending, confirmed, rejection, and retry feedback.
- [ ] Add Processor return shipping fields/action in `RESULT_SUBMITTED`.
- [ ] Add Brand final `결과물 수령 및 승인` MetaMask action in `RETURN_SHIPPED`.
- [ ] Invalidate Processing Job, inventory, custody, material detail, and blockchain transaction queries after projection.
- [ ] Remove the old direct DB receive/approve buttons from these states.
- [ ] Run focused frontend tests.

## Task 8: End-to-end verification and documentation alignment

- [ ] Run the complete Hardhat test suite and compile.
- [ ] Run the complete backend Maven test suite.
- [ ] Run the complete frontend Vitest suite, ESLint, and production build.
- [ ] Run `git diff --check` in all changed repositories.
- [ ] Update policy/ERD text that still says Processing ownership always remains with Brand.
- [ ] Document redeployment, address configuration, ABI regeneration, and role grants required for testing.
- [ ] Review the implementation against every statement in the approved design.
