# Evidence File Access and Deletion

## Goal

Close the two gaps `2026-09-07-file-upload-policy.md` left open: a client that uploaded evidence
still cannot fetch it back, and nothing says when stored evidence may be removed. Both are
required by `S15P21E104-162`.

The upload policy fixed how bytes get in. It said nothing about how they come out, and
`FileStorageService` had no read method at all — while `evidence_file.file_url` holds a storage
key that no client can resolve. Deletion existed only as a raw `delete(storageKey)` used on the
rollback path; there was no rule about who may call it or when it must be refused.

## Scope

- a read path on `FileStorageService`, implemented for both local and S3 backends
- `GET /api/evidence/{id}/content` — authorised streaming of one evidence file
- `DELETE /api/evidence/{id}` — deletion policy, including the on-chain block
- `evidence_file.deleted_at` / `deleted_by`

Not here: a domain-neutral upload endpoint (see "On `S15P21E104-249`" below), evidence manifest
generation, and on-chain anchoring itself — neither exists yet.

## Chosen Approaches

### Files are served through our own server, not a presigned URL

`GET /api/evidence/{id}/content` reads the object through `FileStorageService.read` and streams
it to an authorised caller.

The alternative was an S3/MinIO presigned URL returned in the evidence response. It was rejected
for three reasons, in order of weight:

1. **A presigned link carries no authorisation.** Until it expires it works for anyone holding
   the string — pasted into a chat, captured in a proxy log, leaked through a referrer.
   Evidence is somebody else's material data: a weight slip shows the counterparty, the tonnage
   and the price. "Logged in" is not the bar; "related to this material" is.
2. **The local backend has no equivalent.** Switching `app.storage.type` is one line of
   configuration, so a retrieval path that only exists for S3 means a `local` deployment has no
   retrieval path at all — and that difference does not surface until deploy.
3. **The decision is ours to make, and it needs domain knowledge.** Who may see an evidence file
   follows from registrant and holder relationships. Object storage cannot answer that.

The cost is that evidence bytes traverse the application. It is bounded: 10 MB per file, streamed
rather than buffered (`FileContent` carries an `InputStream`, never a `byte[]`).

**Who may read** (`EvidenceAccessPolicy`): the uploader, the material's registrant, and any
current holder of that material (`material_balance`). Holders are included deliberately — a buyer
who cannot inspect the evidence behind what they received makes traceability pointless. Roles are
not consulted: two SUPPLIERs have nothing to do with each other's evidence, so a role cannot
stand in for a relationship.

**The media type is derived from the stored key's extension**, not from storage metadata. The
local backend has no such metadata, and S3's copy is whatever the client declared at upload. The
extension already passed the whitelist, so deriving it is the narrower choice and keeps both
backends answering identically. Responses carry `Content-Disposition: inline` with no filename
(user-supplied names carry newlines and quotes), `X-Content-Type-Options: nosniff`, and
`Cache-Control: private, no-store`.

This endpoint is the only one that does not use the `ApiResponse` envelope — the body is JPEG or
PDF bytes. Failures still go through `GlobalExceptionHandler`, so 403 and 404 look like every
other API.

### Deletion is soft for the row and hard for the bytes

| Condition | Code | HTTP |
| --- | --- | --- |
| Row absent or already deleted | `EVIDENCE_NOT_FOUND` | 404 |
| Not the uploader and not the material's registrant | `EVIDENCE_DELETE_FORBIDDEN` | 403 |
| Target is anchored on chain | `EVIDENCE_ANCHORED_ON_CHAIN` | 409 |
| Reference type we cannot check yet | `EVIDENCE_REFERENCE_NOT_SUPPORTED` | 400 |

**Anchored evidence cannot be deleted by anyone.** `MaterialToken.verifyEvidence` compares the
recorded hash against the file. Remove the bytes and that comparison is not pending, it is
permanently impossible — the chain side cannot be undone. A buyer loses the ability to check the
history and there is no recovery path.

The check is **per target, not per file**. Only one manifest hash goes on chain, so which files it
covered cannot be recovered from the rows. Deleting any one of them makes the manifest
irreproducible, so a target with `evidence_hash` set locks all of its evidence. `token_id` counts
as anchored too: minting carries evidence with it, so the manifest column being empty is not
proof that nothing was recorded. Where the answer is unknown we refuse — a wrong refusal costs a
support question, a wrong deletion costs the record.

**Delete permission is narrower than read permission.** Holders may read but not delete: if a
buyer could remove the evidence attached to material they acquired, they could strip the history
and resell.

**The row survives.** Hard-deleting it would take `file_hash` with it, and if that hash later
turns up in an on-chain manifest there is no way to say what it was of. What the user wants is for
the file to be gone; what we cannot lose is the fact that the hash existed. So `deleted_at` is
stamped and the stored object is really deleted. `deleted_by` is separate from `uploaded_by`
because registrants can delete too, and "who removed this evidence" is the question an audit asks.

**Order: commit the row, then delete the bytes.** The reverse leaves a live row pointing at a
missing file if the row write fails — every screen rendering it breaks and the file is
unrecoverable. This order can strand an orphan object, which costs disk and is findable by prefix.
That is the same trade the upload policy already chose. Consequently `EvidenceDeletionService`
deliberately has no `@Transactional`: joining a transaction would move the storage delete inside
it and invert the order.

Listing (`GET /api/evidence`) filters `deleted_at IS NULL`. The index becomes
`(reference_type, reference_id, deleted_at)` so that filter is served by the index.

### On `S15P21E104-249` (shared file upload API)

`POST /api/evidence` plus `FileStorageService` already satisfy what `-249` asks for, and a
domain-neutral endpoint should **not** be added:

- Every rule `-245`/`-246` fixed — whitelist, size, count, derived path, UUID naming, streamed
  hashing — lives in `FileContentPolicy` / `FileStorageService`, not in the evidence controller.
  A second caller (product images, dispute attachments) reuses all of it by injecting the service.
- A neutral `POST /api/files` cannot check that the upload belongs to anything, which is precisely
  what `EvidenceReferenceValidator` exists for. `reference_id` has no FK, so without that check a
  row can point at a material that does not exist — and its hash can reach the chain.
- It cannot authorise either. Deciding who may attach a file requires knowing the target domain;
  a neutral endpoint would either check nothing or grow a switch over every domain.
- Two write paths to the same table means the invariants have two places to be enforced, and
  evidence is not recoverable when one of them is wrong.

Product images and dispute attachments are the cases to watch. When they arrive they should get
their own domain endpoints over the same `FileStorageService`, not a shared upload endpoint.

## Verification

`./mvnw -B -ntp test`

- both backends: read returns the written bytes, size and derived media type; a missing object is
  `FILE_NOT_FOUND` (404) rather than a 500; a key escaping the root is refused
- access: uploader, registrant and holder can read; a third party gets 403; deleted evidence is
  404; a row whose object is gone is `FILE_NOT_FOUND`
- deletion: the row is marked and the bytes disappear; the registrant can delete someone else's
  upload; deleted evidence leaves the list; a manifest hash or a minted token blocks deletion for
  everyone and leaves the bytes in place; a third party gets 403 *before* the anchor check, so the
  status code cannot be used to probe whether a material reached the chain; deleting twice is 404
- HTTP surface: `/content` returns raw bytes with the security headers and no envelope, failures
  use the shared envelope, both endpoints require a token

## Operational Note

`evidence_file` gains two columns. The production profile runs `ddl-auto: validate`, so the
running database needs
`ALTER TABLE evidence_file ADD COLUMN deleted_at DATETIME NULL, ADD COLUMN deleted_by BIGINT NULL`
plus the FK and the widened index before this version starts. Without it the application fails at
boot rather than at first use.
