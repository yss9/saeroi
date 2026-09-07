# Processing request creation

## Confirmed policy
- BRAND chooses one material, one processor, and one or more processing purposes.
- Quantity is a positive integer in grams. Available quantity is the owner's material_balance.quantity minus ALL ACTIVE material_lock amounts, across purposes.
- Processor must be active, have role PROCESSOR, and support the material type and every selected purpose at the requested quantity (inclusive bounds).
- Create request in QUOTING and an ACTIVE PROCESSING/PROCESSING_REQUEST lock atomically. Never decrease balance or call blockchain when creating a request.
- Request expires exactly seven days after creation. Cancellation, rejection, and expiry release the request's locks atomically. Selection retains the lock for the Job.
- Brand's quote confirmation is the blockchain recording boundary. Quote/Job creation and contract integration are separate from this request-creation endpoint.

## Implementation
Use existing JPA, JWT sub user identity, ApiResponse, and BusinessException conventions. POST /processing-requests is the specified route; /api/v1/processing-requests is also supported for existing API-prefix conventions. Input: materialId, processorId, quantity, processTypes (ordered, distinct). Return HTTP 201 with requestId, requestCode, status, quantity, materialId, processorId, processTypes, createdAt, expiresAt.

Use PESSIMISTIC_WRITE on the material/owner balance row. Read active locks using a current locking read after acquiring the balance row (including under MySQL REPEATABLE READ). Save request/input/types/lock in the same transaction. Other future lock writers must follow the same balance-row protocol.

Add processor_id and REJECTED to processing_request, and PROCESSING_REQUEST to the material lock reference enum. Preserve existing multi-input SQL tables but accept one material in this API. Add a manual additive MySQL migration for existing databases; do not infer selected processors for legacy requests.

Expose request cancel/reject operations with party authorization. A scheduled expiry worker processes due QUOTING requests in bounded batches and locks each request before releasing its locks. A guarded lock transition supports attaching an existing request lock to a Job without releasing or recreating it; the future quote-selection transaction must invoke it.

## Verification
HTTP integration tests with actual H2 repositories cover successful creation and seven-day TTL, fractional/zero/negative/overflow input, permissions, all-capability and quantity matching, active lock subtraction, rollback and concurrent oversubscription. Lifecycle tests cover authorized termination, expiry and preserved Job lock quantity. Existing regression suite must pass. H2 concurrency results do not substitute for a production MySQL load test.
