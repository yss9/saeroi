# Processing Job Workflow Design

## Scope

Implement the post-payment processing lifecycle for one Brand, one designated Processor, and one input Material per Job. The workflow covers shipping, receiving, work start, draft result registration, MetaMask-signed submission, signed Brand approval or rejection, lock consumption, child Material creation, balances, lineage, evidence, and Material events. Actual blockchain transactions remain behind the existing integration boundary and are outside this change.

## State machine

`CREATED -> PAID -> SHIPPED -> RECEIVED -> PROCESSING -> RESULT_SUBMITTED -> CONFIRMED`

Brand rejection changes `RESULT_SUBMITTED -> REJECTED`. The Processor can edit the result draft in `REJECTED` and submit it again, returning the Job to `RESULT_SUBMITTED`. The input lock remains active until approval. Approval is irreversible and consumes the lock and the Brand's input balance atomically.

## API

- `GET /processing-jobs`: authenticated actor's Brand or Processor Jobs, newest first.
- `GET /processing-jobs/{jobId}`: Brand or Processor party detail.
- `PATCH /processing-jobs/{jobId}/ship`: Brand records optional carrier and tracking number.
- `PATCH /processing-jobs/{jobId}/receive`: Processor confirms receipt.
- `PATCH /processing-jobs/{jobId}/start`: Processor starts work.
- `POST /processing-jobs/{jobId}/results`: Processor replaces the editable result draft. Output quantity is positive integer grams and `sum(outputs) + lossQuantity == inputQuantity`.
- `POST /processing-jobs/{jobId}/signature-challenge`: party receives a one-time MetaMask message for `RESULT_SUBMIT`, `APPROVE`, or `REJECT`.
- `POST /processing-jobs/{jobId}/results/submit`: Processor submits the saved result using a challenge ID and signature. At least one active `PROCESSING_JOB` evidence is required.
- `POST /processing-jobs/{jobId}/approve`: Brand approves the submitted result using a challenge ID and signature.
- `POST /processing-jobs/{jobId}/reject`: Brand rejects using a challenge ID, the same rejection reason bound into the challenge, and signature.

## Signature contract

The server creates a dedicated `processing_signature_challenge` row. It binds job, action, actor, wallet, chain, result revision, payload SHA-256 hash, nonce, canonical message, and expiry. The final command reconstructs the payload hash, verifies `personal_sign` through `WalletSignatureVerifier`, and atomically marks the challenge used. This prevents replay and prevents a signature for one Job/action/result/rejection reason from authorizing another.

## Persistence and approval transaction

`processing_output` stores draft outputs and links each row to its child Material only after approval. The Job stores result revision, rejection reason, shipping data, signature audit fields, and lifecycle timestamps. On approval the service locks the Job, input lock, and Brand balance; validates the submitted revision; consumes the lock; decreases the input balance; creates each processed child Material owned by the Brand; links `processing_output`; registers PROCESSING lineage; records append-only Material events; and confirms the Job in one transaction.

## Evidence and authorization

Processing Job evidence can be uploaded by the Job's Processor while the Job is `PROCESSING` or `REJECTED`. Both Job parties can read it. Existing soft-delete and hash behavior remains. Every command checks the authenticated user's active account, exact Job party, role, and current state.
