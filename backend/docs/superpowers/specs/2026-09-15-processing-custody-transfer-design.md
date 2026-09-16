# Processing Custody Transfer Design

**Date:** 2026-09-15  
**Scope:** ProcessingRegistry contract, backend processing workflow/schema, frontend Processing Job UI

## Goal

Keep a Brand's input Material Token under Brand ownership while it is physically in transit, transfer the locked input quantity to the designated Processor only when that Processor confirms receipt with MetaMask, and complete the return by consuming the Processor-held input and issuing the derived Child Material Tokens to the Brand when the Brand confirms receipt with MetaMask.

All public Processing API paths in this change omit an API version segment. The canonical paths start with `/processing-jobs`.

## Domain decisions

### Outbound custody

`PAID -> SHIPPED` records physical shipment only. The Brand remains the token owner and the existing Processing lock remains active, so the shipped quantity cannot be reused.

`SHIPPED -> RECEIVED` is an on-chain operation initiated by the designated Processor. `ProcessingRegistry.confirmReceipt` atomically:

1. consumes the Brand's Processing lock by transferring the locked input quantity to the Processor;
2. creates a replacement Processing lock owned by the Processor for the same quantity and on-chain Job reference;
3. replaces the Job input's lock identifier with the new lock identifier; and
4. changes the on-chain Job status to `RECEIVED`.

The backend applies the corresponding balance and lock projection only after the confirmed transaction receipt is submitted. Before confirmation, the Brand balance and lock remain unchanged.

### Processing and result review

The Processor cannot use or transfer the received input while the replacement lock is active. Processing can start only after receipt is confirmed.

Result drafts and signed result submission remain separate from physical return shipment. The Brand may reject a submitted result while it is `RESULT_SUBMITTED`. A rejected result returns to the Processor for correction without releasing the Processor's input lock.

The Processor may ship a submitted result. This changes the backend Job to `RETURN_SHIPPED` and records return carrier/tracking details. Once return shipment starts, result rejection is disabled; physical receipt is the remaining final decision.

### Return custody and finalization

The original input Material Token cannot be returned as the result because Processing creates a distinct Child Material with its own lineage and quantity. Therefore the Brand's return receipt transaction performs the Processing finalization atomically:

1. consumes and burns the Processor-owned locked input quantity;
2. mints each approved Child Material Token directly to the Brand;
3. records parent-child lineage in `ProcessingRegistry`; and
4. changes the on-chain Job status to `CONFIRMED`.

This is the return ownership transition: Processor custody ends and the Brand receives the newly derived output tokens. There is no interval in which an unapproved Child Token exists.

After the receipt is confirmed, the backend consumes the Processor lock, decreases the Processor input balance, creates and links Child Material rows, increases Brand output balances, records Loss and Material Events, and completes the Job.

## State model

The backend state sequence is:

```text
CREATED
  -> PAID
  -> SHIPPED
  -> RECEIVED
  -> PROCESSING
  -> RESULT_SUBMITTED
  -> RETURN_SHIPPED
  -> CONFIRMED
```

`RESULT_SUBMITTED -> REJECTED -> PROCESSING/RESULT_SUBMITTED` remains available before `RETURN_SHIPPED`.

The on-chain state sequence uses the existing states through `RESULT_SUBMITTED`, adds `RETURN_SHIPPED`, and finishes at `CONFIRMED`.

## Contract changes

`ProcessingRegistry` becomes the only contract that moves Processing custody. Direct calls to `MaterialToken.safeTransferFrom` are not used by this workflow because the input quantity is locked.

- Extend `IProcessingMaterialToken` with `consumeMaterialLock`.
- Change `confirmReceipt` to transfer each Brand-owned input lock to the Processor and create a replacement Processor-owned Processing lock.
- Emit the old lock ID and replacement lock ID so the frontend can submit an exact projection to the backend.
- Add `markReturnShipped(jobId, evidenceHash)` callable only by the designated Processor in `RESULT_SUBMITTED`.
- Restrict `rejectProcessingResult` to `RESULT_SUBMITTED`.
- Restrict `approveProcessingResult` to `RETURN_SHIPPED`; it consumes the replacement input locks and mints output tokens to the Brand.
- Preserve replay protection through the Job state machine.

Contract tests cover caller ownership, status checks, exact input balances, replacement locks, rejection boundaries, input burning, output minting, lineage, and repeated calls.

## Backend data model

`processing_job` gains:

- `chain_job_id BIGINT NULL UNIQUE`
- `receive_tx_hash CHAR(66) NULL UNIQUE`
- `receive_block_number BIGINT NULL`
- `return_carrier VARCHAR(50) NULL`
- `return_tracking_number VARCHAR(100) NULL`
- `return_shipped_at DATETIME NULL`
- `return_receive_tx_hash CHAR(66) NULL UNIQUE`
- `return_receive_block_number BIGINT NULL`

`processing_input.material_lock_id` is updated from the consumed Brand lock to the replacement Processor lock at receipt. Both lock rows are retained as immutable history: the Brand lock becomes `CONSUMED`, and a new active Processor lock is inserted. The replacement lock stores the emitted on-chain lock ID.

The schema source and a forward migration are updated together. No existing rows are rewritten. Existing in-progress Jobs without a `chain_job_id` cannot use on-chain receipt actions until their chain Job is linked.

## Backend APIs

Canonical paths:

```http
POST  /processing-jobs/{jobId}/chain
PATCH /processing-jobs/{jobId}/ship
PATCH /processing-jobs/{jobId}/receive
PATCH /processing-jobs/{jobId}/start
POST  /processing-jobs/{jobId}/results
POST  /processing-jobs/{jobId}/results/submit
POST  /processing-jobs/{jobId}/reject
PATCH /processing-jobs/{jobId}/return/ship
POST  /processing-jobs/{jobId}/return/receive
GET   /processing-jobs/{jobId}
GET   /processing-jobs
```

`POST /processing-jobs/{jobId}/chain` links a confirmed `createJob` transaction and the emitted on-chain Job and lock identifiers. It is Brand-only and idempotent for the same receipt.

`PATCH /processing-jobs/{jobId}/receive` is Processor-only and accepts the confirmed `confirmReceipt` transaction plus replacement lock identifiers. It validates the Job, actor wallet, expected status, quantities, and identifiers before changing DB balances and locks.

`PATCH /processing-jobs/{jobId}/return/ship` is Processor-only and accepts optional carrier/tracking information plus the confirmed `markReturnShipped` transaction.

`POST /processing-jobs/{jobId}/return/receive` is Brand-only and accepts the confirmed `approveProcessingResult` transaction and emitted output token identifiers in output-row order. It performs final DB consumption, Child Material creation, balance issuance, lineage, events, and Job completion in one database transaction.

Transaction link requests contain `txHash`, `blockNumber`, and event-derived identifiers. Contract address, chain ID, and signer wallet are resolved or checked by the server; client-supplied values never override configured values.

The old `/approve` route is retained temporarily as a compatibility error that directs clients to return receipt; it does not bypass on-chain confirmation. Versioned aliases are not added for the new endpoints.

## Idempotency and failure handling

The chain transaction happens first. The backend projection happens only after `tx.wait()` returns a successful receipt.

Every confirmation endpoint accepts an identical retry after a network failure. If the Job already contains the same transaction hash and identifiers, it returns the current Job. A different hash for an already completed transition is rejected.

Database updates are atomic. If projection fails, the chain remains authoritative and the user can retry the same link request. Unique transaction hashes and state checks prevent double balance movement.

The backend does not accept a personal-sign signature as proof that a token moved. Existing signature challenges remain for result content submission and rejection, where no token transfer occurs.

## Frontend flow

The Processing Job screen uses the configured `ProcessingRegistry` contract and the connected wallet.

- Brand chain Job creation runs when a selected Job is prepared for the on-chain lifecycle and then links its receipt to the backend.
- Brand physical shipment does not transfer tokens.
- Processor `소재 수령 확인` sends `confirmReceipt(chainJobId)`, waits for confirmation, extracts replacement lock events, and calls the unversioned backend receive endpoint.
- Processor result submission retains content signing and validation.
- Processor `결과물 발송` sends `markReturnShipped`, waits for confirmation, and records return shipping in the backend.
- Brand `결과물 수령 및 승인` sends `approveProcessingResult`, waits for confirmation, extracts Child Token IDs, and calls the backend return-receive endpoint.

Buttons show signing, pending, confirmed, rejected, and retry states. DB actions are not offered before the corresponding on-chain receipt exists. Processing Job, inventory, material detail, and custody queries are invalidated after successful projection.

Frontend API calls use `/processing-jobs/...` without `/v1`.

## Inventory behavior

Before Processor receipt, the input appears in the Brand's owned inventory and in the Processor's in-transit custody section. After confirmed receipt, the quantity appears in the Processor's owned inventory but remains marked and locked for the Processing Job.

After Brand return receipt, the consumed input no longer appears as usable Processor inventory. The new Child Materials appear in the Brand inventory. `CONFIRMED` Jobs are removed from active Processor custody.

## Verification

- Solidity unit/integration tests prove both custody transitions and final Child Token ownership.
- Backend integration tests prove role restrictions, transition ordering, identical retries, different-hash rejection, balance and lock projections, Child Material creation, Loss, lineage, events, and transaction records.
- Frontend component/hook tests prove MetaMask calls happen before backend calls and backend calls are skipped on rejected or reverted transactions.
- Full contract, backend, frontend test suites, frontend lint, and production build must pass.

## Deployment constraint

The changed `ProcessingRegistry` bytecode must be redeployed. Its new address must be set consistently in frontend and backend configuration, roles must be granted again, and the generated ABI must be refreshed from the blockchain repository before end-to-end testing.
