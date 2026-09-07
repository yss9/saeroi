# Wallet Signature Login Design

## Goal

Let a user authenticate by signing a one-time challenge with their Ethereum wallet instead of
typing a password. The server issues a nonce, the wallet signs a human-readable message
containing it, and the server recovers the signer address from the signature and compares it to
the address the client claims. On a match the service issues the same access token the rest of
the API already accepts.

## Scope

This change includes:

- `POST /api/v1/auth/wallet/nonce` — issue a single-use nonce and the canonical message to sign.
- `POST /api/v1/auth/wallet/verify` — recover the signer address, compare, and issue a JWT.
- `POST /api/v1/wallets/nonce`, `POST /api/v1/wallets`, `GET /api/v1/wallets/me`,
  `DELETE /api/v1/wallets/{walletId}` — wallet registration, listing, and deactivation.
  **Authenticated**, deliberately outside the permitted `/api/v1/auth/**` prefix so that nobody
  can attach a wallet to somebody else's account.
- `User`, `UserWallet`, and `WalletNonce` entities mapped to the existing `schema.sql` tables.
- EIP-191 (`personal_sign`) signature recovery through `org.web3j:crypto`.
- A domain error enum covering nonce, signature, wallet, and account states.

This change does not include signup, password login, refresh tokens, rate limiting, CORS, or
any change to `SecurityConfig`. It does change `schema.sql` and `ERD.md` — see the wallet policy
section below.

## Chosen Approaches

### Path and security

Both endpoints live under `/api/v1/auth/wallet`. `SecurityConfig` already permits
`/api/v1/auth/**`, so no security change is needed. This is load-bearing rather than cosmetic:
any other prefix returns 401 before reaching the controller. `WalletAuthControllerTests` calls
both endpoints without an `Authorization` header so a future path change fails loudly.

### Unregistered wallets are rejected

`wallet_nonce.user_id` and `user_wallet.user_id` are `NOT NULL` foreign keys, so a wallet must
already belong to an account before a nonce can exist. Rather than work around that with a
synthetic account, the nonce endpoint returns `404 WALLET_NOT_LINKED` for an unknown address.

The trade-off is accepted deliberately. A distinguishable response makes the endpoint an oracle
for "is this address registered", but the UX requires it — "no account for this wallet" and
"something went wrong" call for different user actions. The oracle is weak because wallet
addresses are not guessable; an attacker must already hold a candidate list. Rate limiting is
the real mitigation and is left to nginx.

Rejecting before any write also means an unauthenticated caller cannot make the service allocate
rows for arbitrary addresses.

### Only business roles may log in with a wallet

`user_wallet` rows belong to `SUPPLIER`, `BRAND`, and `PROCESSOR` only — a `CONSUMER` uses the
service without a wallet, and `ADMIN` is not an on-chain actor. Both endpoints therefore reject
a wallet whose owner holds any other role, before issuing a nonce and again before issuing a token.

**The database does not enforce this.** Nothing stops a seed script or a future wallet-linking
endpoint from inserting a `CONSUMER` row, and without the check this service would happily mint a
token for that account. Since roles are the authorization boundary for the rest of the API,
wallet login must not be the one path that bypasses it. `UserRole.canOwnWallet()` holds the list
so a later linking endpoint applies the same rule.

The check runs after the address lookup, so an unregistered address still gets
`WALLET_NOT_LINKED` rather than a role error.

### One active wallet per account, with replacement history kept

`user_wallet` no longer carries `UNIQUE (user_id)`. Accounts may hold several rows; exactly one
is active. Replacing a wallet deactivates the previous row instead of updating or deleting it.

The history matters because six columns snapshot the signing address —
`material_trade.seller_wallet` / `buyer_wallet`, `processing_job.brand_wallet` /
`processor_wallet`, `production_batch.producer_wallet`, and
`blockchain_transaction.from_wallet` — all `NOT NULL`. Losing the row makes it impossible to
tell whose address a snapshot was. Splitting wallets out of `users` was motivated by exactly
this, and repeating the overwrite inside `user_wallet` would defeat it.

Active singleness is enforced by the database through a generated column:

```sql
active_user_id BIGINT GENERATED ALWAYS AS (CASE WHEN is_active THEN user_id ELSE NULL END),
CONSTRAINT uk_user_wallet_active_user UNIQUE (active_user_id)
```

MySQL 8 has no partial unique index. Mapping inactive rows to `NULL` works because `NULL`s do
not collide in a UNIQUE index. `IF()` is avoided because H2 — which the schema tests load
`schema.sql` into — cannot parse it inside a generated column.

The entity does not map `active_user_id`; the database computes it. `UserSchemaMappingTests`
therefore skips generated columns, and `UserWalletActiveConstraintTests` verifies the constraint
by running SQL against `schema.sql` in raw H2. The application test profile is `create-drop`, so
the column does not exist in entity-generated tables and no ordinary test could check it.

**Wallets are not used N-at-a-time.** `material_balance` keys ownership by
`UNIQUE (material_id, owner_id)` — by user. Concurrent wallets would split on-chain balances by
address while the database allows one row per user. Raising that limit would mean redefining
balance, locks, and trades around addresses.

Login uses `findByWalletAddressAndActiveTrue`: a deactivated wallet is treated as absent, and a
nonce issued before deactivation is rejected at verify.

### The signed message is a cross-branch contract

`WalletLoginMessage` holds the exact string. The frontend builds the same string, but the
**server's copy is canonical**: the nonce response carries `message`, and the client signs that
value after checking it equals what it would have built. A divergence therefore surfaces before
a signature exists, instead of as a blanket `SIGNATURE_MISMATCH` in production.

The message is Korean and human-readable because MetaMask shows it verbatim; the user judges
what they are signing from that text alone. It states that no assets move.

### Address normalization

Addresses are validated against `^0x[0-9a-fA-F]{40}$` and stored, queried, and compared in
lower case. The EIP-55 checksummed form used in the message is computed by the server from the
lower-case value; the casing a request supplies is never trusted.

Normalizing is not optional. MySQL's `utf8mb4_0900_ai_ci` collation is case-insensitive, so
`uk_user_wallet_address` would catch duplicates in production, but H2 in tests is
case-sensitive. Without normalization the test suite stays green while production accumulates
duplicate rows.

### Nonce single use

`WalletNonceRepository.markUsed` is a conditional update:

```sql
update wallet_nonce set used_at = :now where id = :id and used_at is null
```

The affected-row count is the guarantee. InnoDB takes a row lock, so exactly one of two
concurrent requests sees `1`; no application lock is involved and the property holds across
instances. `WalletNonceRepositoryTests` asserts it at the SQL level and
`WalletAuthServiceTests` asserts it with two threads.

The update runs before signature recovery so the winner is decided before the expensive work.
A failed verification rolls the transaction back, which restores the nonce — that is intended.
Burning a nonce on failure would need a separate transaction and buys nothing, because a
signature cannot be produced without the private key.

### Entities do not extend `BaseEntity`

`users` and `user_wallet` have `created_at`/`updated_at` columns that would fit `BaseEntity`,
and `wallet_nonce` has no `updated_at` at all. All three declare their audit fields inline,
following `Material`. `UserSchemaMappingTests` compares each entity's declared `@Column` names
against `schema.sql`; fields inherited from a superclass would be invisible to that comparison,
which is the only check standing between a mapping mistake and a failed production startup
under `ddl-auto: validate`.

### Chain ID

`WalletNonceRequest.chainId` is optional. The value written to `wallet_nonce.chain_id` is always
copied from `user_wallet.chain_id`, so the `CHECK (chain_id > 0)` constraint holds regardless of
what the client sends. When the request does supply a value it is compared, and a mismatch
returns `WALLET_CHAIN_MISMATCH` — this catches "the user switched MetaMask to another network"
at nonce issuance rather than after they have signed.

## Dependencies

```xml
<web3j.version>4.12.3</web3j.version>
<dependency>
  <groupId>org.web3j</groupId>
  <artifactId>crypto</artifactId>
  <version>${web3j.version}</version>
</dependency>
```

`org.web3j:crypto`, not `org.web3j:core` — node RPC clients are not needed. BouncyCastle arrives
transitively. If the blockchain work later adds `org.web3j:core`, align on `${web3j.version}` so
two BouncyCastle versions do not land on the classpath.

`Sign.signedPrefixedMessageToKey` applies the EIP-191 prefix itself. The prefix embeds the
**UTF-8 byte length**; the Korean login message is 140 characters but 210 bytes, so a hand-rolled
prefix using `String.length()` fails every verification. `WalletLoginMessageTests` pins both
numbers and `WalletSignatureVerifierTests` covers recovery from the Korean message specifically.

## Package Structure

Everything lives under `com.ssafy.upcycle.user`; no new top-level package is introduced, since
`PackageStructureTests` checks a closed list and the package-structure spec allows each domain
to add its own subpackages. Entity classes sit in `entity/`, following `material`.

```
user/
├── WalletAuthErrorCode.java
├── config/       WalletAuthProperties, WalletAuthConfig
├── entity/       User, UserRole, UserStatus, UserWallet, WalletNonce, WalletAddresses
├── repository/   UserRepository, UserWalletRepository, WalletNonceRepository
├── service/      WalletLoginMessage, WalletSignatureVerifier, WalletAuthService
├── controller/   WalletAuthController
└── dto/          WalletNonceRequest/Response, WalletVerifyRequest, WalletLoginResponse,
                  UserSummaryResponse
```

`WalletSignatureVerifier` is the only class that knows web3j. It needs no Spring context and
holds the logic most prone to subtle error, so it is isolated and unit-tested on its own.

## Error Contract

`code` is the machine contract the frontend branches on; `message` is what the user reads.

| Code | Status | Meaning |
| --- | --- | --- |
| `INVALID_WALLET_ADDRESS` | 400 | Address does not match the expected format |
| `INVALID_CHAIN_ID` | 400 | Chain id is not a positive number |
| `WALLET_NOT_LINKED` | 404 | No account owns this address |
| `WALLET_CHAIN_MISMATCH` | 400 | Requested chain differs from the registered one |
| `NONCE_NOT_FOUND` | 400 | No such nonce for this address |
| `NONCE_EXPIRED` | 400 | Nonce TTL elapsed |
| `NONCE_ALREADY_USED` | 400 | Nonce was consumed |
| `INVALID_SIGNATURE` | 400 | Signature is malformed or unrecoverable |
| `SIGNATURE_MISMATCH` | 401 | Recovered address differs from the claimed one |
| `USER_NOT_FOUND` | 404 | Wallet points at a missing account |
| `USER_SUSPENDED` | 403 | Account is suspended |
| `WALLET_ROLE_NOT_ALLOWED` | 403 | Owner's role may not use wallet login |
| `WALLET_ALREADY_LINKED` | 409 | Address already belongs to another account |
| `WALLET_NOT_FOUND` | 404 | No such wallet for this user (also hides other users' ids) |
| `WALLET_ALREADY_INACTIVE` | 400 | Wallet is already deactivated |

`NONCE_NOT_FOUND` carries a deliberately vague message so probing other users' nonces reveals
nothing; the code stays precise.

Signatures, recovered addresses, and raw nonces never appear in logs or responses. The DTOs are
records, whose generated `toString` would print every field, so they are never passed to a
logger and no `@ToString` is added.

## Verification

`./mvnw -B -ntp test` covers the whole feature. Manual smoke testing additionally needs seed
rows, because no signup endpoint exists — see the plan document's verification section.
