# Material Register and Detail Implementation Plan

**Goal:** Let `SUPPLIER` and `BRAND` register a material batch and let any authenticated user read
one back. Registration writes DB rows only; the on-chain mint and its confirmation are separate.

**Architecture:** `MaterialController` exposes both endpoints under `/api/materials`, which needs
no `SecurityConfig` change because `anyRequest().authenticated()` already covers it.
`MaterialRegistrationService` owns the write path, `MaterialQueryService` the read path — they are
split because the read side grows balance aggregation next while the write side grows confirm.
`MaterialCodeGenerator` owns `material_code` allocation. `MaterialQuantityUnit` is the single
place unit conversion happens.

**Tech Stack:** Java 21, Spring Boot 3.5.16, Spring Security OAuth2 Resource Server, JPA,
JUnit 5, AssertJ, MockMvc, H2, Maven Wrapper

**Spec:** `docs/superpowers/specs/2026-09-07-material-register-and-detail-design.md`

**Jira:** `S15P21E104-149` (register, 2pt) and `S15P21E104-178` (detail, 1pt)

## Global Constraints

- No change to `SecurityConfig`, `schema.sql`, or `ERD.md`. Entities gain behaviour, not columns.
- Paths carry no `/v1`. The wallet endpoints keep theirs; the whole surface migrates together
  when email login lands, with the frontend in the same cycle.
- Quantity is always an integer number of grams in storage. The request carries a unit and the
  server converts; the unit itself is never stored.
- `generationType` is never an input. Registration is always `INITIAL`.
- The registrant comes from the JWT `sub`, never from the request body.
- `material_code` derives from the persisted `id`. Nothing counts rows to allocate it.
- Registration leaves `token_id`, `chain_id`, and `contract_address` null. That is the state,
  not a bug.

## File Map

New, under `src/main/java/com/ssafy/upcycle/material/`:

- `MaterialErrorCode.java` — domain failures, `ErrorCode` shape without Lombok
- `controller/MaterialController.java` — both endpoints
- `dto/MaterialRegisterRequest.java`, `dto/MaterialRegisterResponse.java`
- `dto/MaterialDetailResponse.java`, `dto/MaterialOwnerResponse.java`
- `dto/MaterialQuantityUnit.java` — `G`, `KG`, `toGrams`
- `dto/IntegralLongDeserializer.java` — rejects fractional numbers for `Long` fields
- `service/MaterialRegistrationService.java`, `service/MaterialQueryService.java`
- `service/MaterialCodeGenerator.java`

Modified:

- `material/entity/Material.java` — `assignCode`, `MATERIAL_CODE_MAX_LENGTH`,
  `TEMPORARY_CODE_PREFIX`. No new mapped column.
- `user/entity/UserRole.java` — `canRegisterMaterial()`
- `material/repository/MaterialBalanceRepository.java` — `findAllByMaterialId`

Reused as-is: `Material.register`, `MaterialRepository`, `MaterialVerificationState`,
`ApiResponse`, `BusinessException`, `GlobalExceptionHandler`, `UserRepository`,
`JwtTokenService`.

## Tasks

### Task 1: Error codes, unit, request/response DTOs

`MaterialErrorCode` covers only rules that pass request validation but fail the domain —
bean-validation failures already surface as 400 through the common handler.

`MaterialQuantityUnit.toGrams` uses `Math.multiplyExact`. A silent overflow wraps negative, and
that value violates `chk_material_quantity` as an unexplainable 500; the service converts the
`ArithmeticException` into a 400.

### Task 2: Code generator

Two-step allocation, because `material_code` is `VARCHAR(20) NOT NULL` unique and the readable
form derives from `id`:

1. `placeholder()` — `TMP-` plus 16 hex characters, exactly 20 chars, from `SecureRandom`
2. `save()` assigns `id`
3. `of(id)` — `MAT-%06d`

`SecureRandom` is not about secrecy; it is so two requests in the same millisecond cannot draw
the same placeholder, which a time-based value cannot promise.

### Task 3: `Material.assignCode`

Only replaces a code that still carries the temporary prefix. A material code is the human
identifier — once it has left the building, changing it desynchronises paperwork, QR codes, and
conversations. Refusing re-allocation closes that path structurally.

### Task 4: Role predicate

`UserRole.canRegisterMaterial()` is `SUPPLIER` or `BRAND`. `PROCESSOR` is deliberately absent:
material a processor produces is the output of processing confirmation, and allowing direct
registration would let material with no processing history be filed under a processor's name.

The list differs from `canOwnWallet()` and must stay separate. Merging them widens one of the two.

The service re-reads the user from the database instead of trusting the token's `roles` claim,
because a token is a snapshot and material registration leads to an irreversible on-chain mint.

### Task 5: Registration service and controller

`@Transactional` spans save and `assignCode`, so a failure rolls the placeholder back.

### Task 6: Query service and detail response

Six response blocks matching the ticket: basic info, quantity, owner, evidence, verification,
status. `verificationState` is a constant `REGISTERED` — see the spec for why, and the team notice
for the warning that goes with it.

`owners` reads `material_balance` through a new `findAllByMaterialId`. `available` is not computed
here; that belongs to `S15P21E104-378`, and computing it twice would let the two drift.

### Task 7: Full suite

`./mvnw -B -ntp test`.

## Verification

Run `./mvnw -B -ntp test`. The suite covers, among others:

- both endpoints return 401 without a token — this fails loudly if the path ever moves under a
  permitted prefix
- `PROCESSOR` and `CONSUMER` are rejected with `MATERIAL_ROLE_NOT_ALLOWED`
- `50 KG` stores `50000`
- `unit: "TON"`, `quantity: 0`, `quantity: 50.5`, a missing `name`, a missing `unit`, and an
  unknown `materialType` are all 400
- two identical requests receive different codes
- no row whose code starts with `TMP-` survives a successful registration
- a request carrying `generationType: "PROCESSED"` still stores `INITIAL`
- `tokenId`, `chainId`, `contractAddress` are null after registration
- detail returns all six blocks, `verificationState` is `REGISTERED`, `owners` is `[]` with no
  balance rows and has two entries with two seeded rows, and an unknown id is 404
- `GET /api/health` still answers 200 in the common envelope

### Correction log

**`quantity: 50.5` was expected to fail and did not.** Jackson's
`DeserializationFeature.ACCEPT_FLOAT_AS_INT` is on by default, so `50.5` bound to `Long` as `50`
and the request succeeded with a quantity 500 g short. `@Positive` never saw a problem because by
then the value was a valid positive integer.

Fixed with `IntegralLongDeserializer` on the field rather than by disabling the mapper feature
globally. A global switch would change how every other part's endpoints parse numbers in the same
commit, and other people's endpoints starting to return 400 is the worse failure.

**The same trap remains on every other integer field in the project** — amounts, `chainId`,
future quantities. A project-wide policy needs team agreement, so it goes in the notice instead of
this branch.

## Follow-ups

Blocked on other people, tracked in `docs/notices/`:

- **`POST /materials/{id}/confirm` has no Jira ticket.** Without it `token_id`, `chain_id`, and
  `contract_address` stay null forever and registered material never reaches the chain.
- **`GET /materials` (list) has no ticket.** Detail and my-materials exist; the filtered list does not.
- **`verificationState` cannot be derived.** `signed_at` and `signer_wallet` are absent from
  `schema.sql`; a real answer needs an on-chain read path or that column.
- **`evidence_file` has no entity.** The evidence file list is appended when `S15P21E104-160` lands.
- **Material `Lock` read/write APIs have no ticket**, so `available` will be computable
  (`S15P21E104-378`) while the reason a material is locked stays invisible.
