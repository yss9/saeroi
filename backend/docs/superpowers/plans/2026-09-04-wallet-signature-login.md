# Wallet Signature Login Implementation Plan

**Goal:** Authenticate a user from an Ethereum wallet signature — issue a single-use nonce,
recover the signer address from the signed message, compare it to the claimed address, and issue
the existing access token.

**Architecture:** `WalletAuthController` exposes two unauthenticated endpoints under the already
permitted `/api/v1/auth/**` prefix. `WalletAuthService` orchestrates persistence and token
issuance. `WalletSignatureVerifier` owns EIP-191 recovery and is the only class that knows web3j.
`WalletLoginMessage` holds the exact signed string, which is a contract shared with the frontend.

**Tech Stack:** Java 21, Spring Boot 3.5.16, Spring Security OAuth2 Resource Server, JPA,
web3j-crypto 4.12.3, JUnit 5, AssertJ, MockMvc, H2, Maven Wrapper

**Spec:** `docs/superpowers/specs/2026-09-04-wallet-signature-login-design.md`

## Global Constraints

- No change to `schema.sql`, `ERD.md`, or `SecurityConfig`.
- No signup, wallet-linking endpoint, refresh token, or rate limiting.
- Unregistered wallets are rejected; no row is created for an unknown address.
- The signed message is a cross-branch contract — it changes only together with the frontend.
- Signatures, recovered addresses, and raw nonces never reach logs or responses.
- New entities declare audit fields inline and must not extend `BaseEntity`.
- Wallet addresses are stored, queried, and compared in lower case.

---

## File Map

- `pom.xml`: `org.web3j:crypto` and the `web3j.version` property.
- `src/main/resources/application.yml`, `src/test/resources/application.yml`: `app.auth.wallet.nonce-ttl`.
- `user/config/WalletAuthProperties.java`: validated nonce TTL.
- `user/config/WalletAuthConfig.java`: `@EnableConfigurationProperties` — this project does not use `@ConfigurationPropertiesScan`.
- `user/WalletAuthErrorCode.java`: the error contract.
- `user/entity/User.java`, `UserRole.java`, `UserStatus.java`: account.
- `user/entity/UserWallet.java`: linked wallet, `markVerified`.
- `user/entity/WalletNonce.java`: single-use challenge, `isExpiredAt`, `isUsed`.
- `user/entity/WalletAddresses.java`: format check and lower-case normalization, free of web3j.
- `user/repository/*Repository.java`: lookups plus the conditional `markUsed` update.
- `user/service/WalletLoginMessage.java`: the signed string, in one place.
- `user/service/WalletSignatureVerifier.java`: EIP-191 recovery, `v` normalization.
- `user/service/WalletAuthService.java`: nonce issuance and verification.
- `user/dto/*.java`: request and response records.
- `user/controller/WalletAuthController.java`: the two endpoints.
- `src/test/java/com/ssafy/upcycle/user/WalletTestSignatures.java`: fixed keys and a `personal_sign`-shaped signer, shared across test packages.

---

### Task 1: Dependency and Nonce TTL

- [x] Add `org.web3j:crypto` with a pinned `${web3j.version}`.
- [x] Add `app.auth.wallet.nonce-ttl` (default `PT5M`) to both `application.yml` files.
- [x] `WalletAuthProperties` rejects a null, zero, or negative TTL; `WalletAuthConfig` registers it.
- [x] Confirm the build still resolves before writing any feature code — a dependency clash here
      would otherwise be misread as a bug in later tasks.

### Task 2: Entities

- [x] `User` with `email`, `password`, `name`, `phone`, `role`, `status`, `created_at`, `updated_at`.
      `password` is mapped but never leaves the server. `suspend()` exists so the suspended branch
      is reachable in tests.
- [x] `UserWallet` with a raw `Long userId` (no `@ManyToOne`, following `Material.registrantId`)
      and `markVerified`.
- [x] `WalletNonce` with no `updated_at` mapping.
- [x] `WalletAddresses.normalize` validates and lower-cases.
- [x] `UserSchemaMappingTests` compares all three against `schema.sql`, plus the two enum columns.
- [x] `UserWalletTests`, `WalletNonceTests` for factory validation and expiry boundary.

**Note:** the first run of the mapping test failed and was correct to. `backend/develop` had
gained `users.updated_at` and `wallet_nonce.created_at` shortly before this work started, and the
entities were written against the older shape.

### Task 3: Repositories

- [x] `findByWalletAddressAndNonce` — deliberately not `findByNonce`, so wallet A's nonce cannot
      be redeemed under wallet B even before the signature is examined.
- [x] `deleteByWalletAddressAndUsedAtIsNull` — one live nonce per wallet.
- [x] `markUsed` conditional update returning the affected-row count.
- [x] `WalletNonceRepositoryTests` asserts `markUsed` returns 1 then 0.

### Task 4: Error Codes and DTOs

- [x] `WalletAuthErrorCode` following `CommonErrorCode`'s shape.
- [x] Request records carry `@NotBlank` + `@Pattern`; `chainId` is optional and only `@Positive`.
- [x] `UserSummaryResponse` mirrors the frontend `UserProfile` and omits `password`.

### Task 5: Message and Verifier

- [x] `WalletLoginMessage.build` produces the six-line string.
- [x] `WalletLoginMessageTests` pins the literal, the absence of `\r`, 140 characters, and
      **210 UTF-8 bytes** — the byte count is what the EIP-191 prefix uses.
- [x] `WalletSignatureVerifier.recoverAddress` parses 65 bytes, normalizes `v` from 0/1 to 27/28,
      and delegates the prefix to `Sign.signedPrefixedMessageToKey`.
- [x] `WalletSignatureVerifierTests` covers the Korean message, `v` normalization, out-of-range
      `v`, wrong lengths, non-hex input, and a signature over a different message.

### Task 6: Service

- [x] `issueNonce`: normalize, look up the wallet, compare chain id when supplied, reject a
      suspended account **before** the wallet popup, delete prior unused nonces, generate 16 random
      bytes as 32 hex characters, persist, and return the canonical message.
- [x] `verify`: find by address and nonce, reject used or expired, consume via `markUsed`,
      rebuild the message, recover, compare case-insensitively, mark the wallet verified, and issue
      the token with an unprefixed role name.
- [x] Load `UserWallet` **after** `markUsed` — `clearAutomatically = true` empties the persistence
      context, so an earlier read would make `markVerified` a no-op.
- [x] `WalletAuthServiceTests` with a mutable `Clock`, including two threads racing one nonce.

### Task 7: Controller

- [x] Two `@PostMapping` methods returning `ApiResponse` directly.
- [x] `WalletAuthControllerTests`: reachable without an `Authorization` header, no `password` in
      the response, the issued token decodes to the user id with an unprefixed role, validation
      errors, and an error body that does not echo the signature.

### Task 8: Full suite

- [x] `./mvnw -B -ntp test` — 154 tests pass.

**Note:** one repository test passed alone and failed in the full run. The `@SpringBootTest`
classes commit to the same in-memory H2 without rolling back, so their leftover rows reached a
`@DataJpaTest` assertion that counted with `findAll()`. Fixed by asserting on specific rows and
by adding `@AfterEach` cleanup to the `@SpringBootTest` classes.

---

## Verification

```bash
./mvnw -B -ntp test
```

### Manual smoke test

**Seed data is a prerequisite.** There is no signup endpoint, so without a `users` +
`user_wallet` row every nonce request correctly returns `WALLET_NOT_LINKED` — which reads like a
broken endpoint if you do not know this.

```sql
INSERT INTO users (email, password, name, phone, role, status)
VALUES ('supplier@example.com', '{noop}dev-only', '개발자', '010-0000-0000', 'SUPPLIER', 'ACTIVE');

INSERT INTO user_wallet (user_id, wallet_address, chain_id, verified)
VALUES (LAST_INSERT_ID(), LOWER('0xYOUR_METAMASK_ADDRESS'), 1337, FALSE);
```

Insert `wallet_address` in lower case to match the application's normalization, and set
`chain_id` to the chain MetaMask is actually connected to — otherwise every request returns
`WALLET_CHAIN_MISMATCH`.

```bash
curl -s -X POST localhost:8080/api/v1/auth/wallet/nonce \
  -H 'Content-Type: application/json' \
  -d '{"walletAddress":"0xYOUR_METAMASK_ADDRESS","chainId":1337}'
```

Sign the returned `message` in the wallet, then post `walletAddress`, `nonce`, and `signature`
to `/api/v1/auth/wallet/verify`.

### Before merging

Start the application against **real MySQL**, not just H2. `users.role` and `users.status` are
MySQL `ENUM` columns mapped as `@Enumerated(STRING)`, and Connector/J reports `ENUM` as `CHAR`,
which `ddl-auto: validate` can reject. `Material` already ships the same pattern, so this is
expected to pass — but H2 cannot prove it.

## Follow-ups

- Rate limiting on `/api/v1/auth/` (nginx level) — the nonce endpoint is unauthenticated and
  reveals whether an address is registered.
- Scheduled cleanup of used and long-expired `wallet_nonce` rows.
- A wallet-linking endpoint, so seeding stops being a manual SQL step.
