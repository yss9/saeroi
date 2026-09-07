# Material Register and Detail Design

## Goal

Let a `SUPPLIER` or `BRAND` register a material batch, and let any authenticated user read one
back. Registration writes DB rows only — the on-chain mint and its confirmation are a separate,
not-yet-ticketed step. This is the first service/controller layer over the material entities that
already exist, so it also fixes the request/response contract that the frontend screens attach to.

Jira: `S15P21E104-149` (register) and `S15P21E104-178` (detail).

## Scope

This change includes:

- `POST /api/materials` — register a material. `SUPPLIER` and `BRAND` only.
- `GET /api/materials/{materialId}` — read one material. Any authenticated user.
- A quantity unit contract: the request carries `quantity` plus `unit`, the server converts to
  grams, and only grams are stored.
- `material_code` allocation derived from the persisted identity.
- A domain error enum for the material package.
- `Material` gains a deferred-code factory and `assignCode`. No schema change.

This change does not include the on-chain mint, the signature-result endpoint
(`POST /materials/{id}/confirm`, which has no Jira ticket), the material list endpoint, evidence
file upload, balance/available aggregation, locks, lineage, or any change to `SecurityConfig` or
`schema.sql`.

## Chosen Approaches

### Path has no version segment

Both endpoints live at `/api/materials`, not `/api/v1/materials`. The Jira tickets and the
endpoint map both spell it without a version, and the material family should not be split across
two conventions mid-resource.

This does leave two conventions in one server: the wallet endpoints are `/api/v1/auth/wallet/*`
and `/api/v1/wallets/*`, everything else is unversioned. That divergence is pre-existing and
recorded; the agreement is that the whole surface moves to `/v1` at once when the email-login
controller lands, with the frontend changed in the same cycle. Do not migrate one side alone.

No security change is required. `SecurityConfig` permits `/api/v1/auth/**` and `GET /api/health`
and then falls through to `anyRequest().authenticated()`. `/api/materials` is not in the permit
list, so it requires a token by default — which is exactly the intent for both endpoints.

### The unit is validated but never stored

There is no `unit` column. `material.total_quantity` is `BIGINT` and the domain rule is that
material quantity is always an integer number of grams, with kilogram display being a
presentation concern.

Accepting only grams would push conversion onto every client and make a wrong scale factor
invisible to the server. Instead the request carries both fields:

```
{ "quantity": 50, "unit": "KG" }   ->  total_quantity = 50000
```

`quantity` is bound as a `long`, so `50.5` fails at deserialization rather than silently
truncating. `unit` is an enum of `G` and `KG`; anything else is a 400 before the service runs.
The unit itself is discarded after conversion, which keeps the stored representation canonical
and needs no migration.

### `material_code` is derived after the insert

`material_code` is `VARCHAR(20) NOT NULL` with a unique constraint, so a value must exist at
insert time, and the readable form the planning documents use is `MAT-000001`.

Allocating from `MAX(id) + 1` breaks under concurrent registration: two transactions read the
same maximum and the second insert violates `uk_material_code`. A separate sequence table would
work but changes `schema.sql`, which is shared across parts.

So the code is derived from the identity the database already allocates:

1. build the entity with a placeholder — `TMP-` followed by 16 hex characters, exactly 20 chars
2. save, which assigns `id`
3. call `assignCode(id)` to replace the placeholder with `MAT-%06d`

All three steps run in one transaction, so a failure rolls the placeholder back rather than
leaving it visible. A test asserts that no row whose code starts with `TMP-` survives a
successful registration, because a leaked placeholder would be a unique but meaningless code.

The cost is one extra `UPDATE` per registration. That is accepted: it buys collision-freedom from
the database itself and a code that maps one-to-one onto `id`, which makes support questions
answerable without a lookup table.

### `generationType` is not an input

`Material.register` fixes `generationType` to `INITIAL`. `PROCESSED` and `PRODUCTION_SCRAP` are
outcomes of processing and production confirmation, so accepting them from a registration request
would let a client mint a "processed" material that never went through a processor — the exact
claim the trust model is built on. The request DTO has no such field.

### The detail response reports a verification state it cannot compute

The ticket lists Verification as a response block, so the field ships. It is always
`REGISTERED`, and that is a limitation rather than a fact about the material.

`MaterialVerificationState` exists as an enum, but `material` has no column for it, and the basis
the schema design assumed for deriving it — `signed_at` — is not in `schema.sql` either. Neither
is `signer_wallet`. A real answer requires reading
`MaterialToken.verificationRecord(tokenId, SOURCE_VERIFIED)` on chain, or finding a successful
`verifySource` transaction in `blockchain_transaction`. Both are outside these two tickets.

Returning a constant is worse than returning nothing if nobody says so, because the frontend
Blockchain Proof component would render a verification badge from it. The team notice and the
ticket comment both state that the value is not yet meaningful, and a separate request document
asks for the missing basis — either an on-chain read path or a `signed_at` column.

### Owner comes from the balance projection, without the available calculation

Ownership is not a column on `material`. `registrant_id` records who registered the batch, which
is not the same as who holds it after a trade. Current holders live in `material_balance`, keyed
`UNIQUE (material_id, owner_id)`.

`MaterialBalanceRepository` has `findByMaterialIdAndOwnerId` and `findAllByOwnerId` but no lookup
by material alone, so one is added. Detail is a single-material read, so there is no N+1 concern
here; the existing comment warning about that refers to list queries.

The response carries `quantity` as stored and deliberately omits `available`.
`available = quantity − Σ(ACTIVE lock)` belongs to `S15P21E104-378`, and computing it in two
places would let the two drift.

Each entry carries `syncedAt`. The projection is filled by `S15P21E104-376`, which is itself
blocked, so `owners` is an empty list today. Without a timestamp, a caller cannot tell "this
material has no holders" from "the projection has never run", and both look like a bug.

### Evidence is limited to the columns on `material`

`evidence_file` exists in `schema.sql` but has no JPA entity, and creating one belongs to
`S15P21E104-160`. Building it here would duplicate that ticket and collide with it on review.

So the Evidence block is `evidenceHash` and `manifestUrl`, both read from `material`. Both are
null until an evidence upload path exists. The file list is appended once `-160` lands. This
partially satisfies the ticket, and the ticket comment records why.

### On-chain mapping fields are exposed as nullable

`token_id`, `chain_id`, and `contract_address` are on `material` but appear in no planning
document. They are the mint result, and the endpoint that records it has no ticket, so they are
null for every row this change creates.

They are still returned. A null `tokenId` is the only signal the frontend has for "registered but
not yet on chain", and inventing a separate boolean for it would drift from the columns.

## Package Structure

```
material/
  MaterialErrorCode.java
  controller/MaterialController.java
  dto/MaterialRegisterRequest.java
     MaterialRegisterResponse.java
     MaterialDetailResponse.java
     MaterialOwnerResponse.java
     MaterialQuantityUnit.java
  service/MaterialRegistrationService.java
     MaterialQueryService.java
     MaterialCodeGenerator.java
```

Registration and query are separate services. They share no state, and the query side will grow
balance aggregation in `-377`/`-378` while the write side grows the confirm step.

The controller returns `ApiResponse` directly and throws `BusinessException` for failures, the
same shape `WalletAuthController` uses, so `GlobalExceptionHandler` decides status codes in one
place.

## Error Contract

| Code | Status | When |
| --- | --- | --- |
| `MATERIAL_NOT_FOUND` | 404 | detail lookup for an unknown id |
| `MATERIAL_ROLE_NOT_ALLOWED` | 403 | registrant is not `SUPPLIER` or `BRAND` |
| `MATERIAL_QUANTITY_INVALID` | 400 | quantity is not positive, or grams overflow `BIGINT` |
| `MATERIAL_UNIT_NOT_SUPPORTED` | 400 | unit outside `G`/`KG` |

Bean-validation failures on required fields surface through the existing handler as 400 with the
common envelope, so they need no dedicated code.

## Verification

`./mvnw -B -ntp test` must pass with no failures. `Material` changes, so a schema-mapping test is
required — the test profile is `create-drop`, and nothing else in the suite catches an
entity/`schema.sql` mismatch.

Beyond the unit and slice tests, the endpoints are exercised over HTTP: both return 401 without a
token; registration converts `50 KG` to `50000`; `unit: "TON"` and `quantity: 50.5` are rejected;
two identical requests produce different codes; no `TMP-` code survives; detail returns all six
response blocks, `verificationState` is `REGISTERED`, `owners` is `[]` with no rows and length 2
with two seeded rows, and an unknown id is 404. `GET /api/health` is re-checked so the deployment
health probe is known not to have regressed.
