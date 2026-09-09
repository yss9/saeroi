# Material Verification 판정 근거 확정

- 티켓: S15P21E104-433 (`[COMMON]`), 구현은 S15P21E104-434 (`[BE]`)
- 결정자: 공병우 (Material 관리 · 공통)
- 날짜: 2026-09-07

## 먼저 — 내가 퍼뜨린 잘못된 전제를 바로잡는다

나는 여러 곳에 **"서버가 `verificationState` 를 항상 `REGISTERED` 로 준다"** 고 적었다.
프론트엔드 `getMaterial.ts` 주석, `MaterialDetailPage` 주석, `-180` 의 자리표시자 문구,
그리고 이 티켓(`-433`)의 설명까지 그 전제로 썼다.

**사실이 아니다.** `MaterialVerificationStateResolver` 는 이미 두 단을 판정한다.

```java
if (tradeVerification.hasBuyerSignedTrade(materialId)) {
    return MaterialVerificationState.TRANSACTION_VERIFIED;
}
return MaterialVerificationState.REGISTERED;
```

`material_trade.buyer_signed_at` 은 **정책 09.1 의 확정 조건 다섯 가지**(결제 · 배송 ·
수령 확인 · 지갑 서명 · 보유량 이전)가 한 트랜잭션에서 모두 통과해야만 기록된다.
하나라도 어긋나면 수령 확인 자체가 거부되어 시각이 남지 않는다.
따라서 `TRANSACTION_VERIFIED` 는 이미 **근거 있는 값**이다.

내가 "판정 근거가 DB 에 없다" 고 본 것은 `material.signed_at`(SOURCE_VERIFIED 용) 하나가
없다는 사실을 전체로 확대한 것이었다. 확인하지 않고 단계 전체로 넓혔다.

## 결정 — 기존 설계를 바꾸지 않는다. 빈 단을 채운다

`MaterialVerificationStateResolver` 의 방식은 **이미 결정되어 근거까지 적혀 있다.**

> 컬럼으로 두면 단계가 올라갈 때마다 UPDATE 하는 코드가 필요하고, 한 군데라도 빠뜨리거나
> 트랜잭션이 중간에 실패하면 "가공은 승인됐는데 상태는 그대로" 인 불일치가 남는다.

그리고 도메인 간 참조 방향을 지키는 방법도 이미 정해져 있다 — `TradeVerification` 처럼
**material 이 질문(포트)을 선언하고 사실을 가진 도메인이 구현**한다. trade 가 이미
material 을 여러 곳에서 참조하므로, material 이 trade 리포지토리를 직접 끌어다 쓰면
순환 참조가 된다.

처음에 나는 "모든 단을 `blockchain_transaction` 하나에서 파생" 하는 안을 적었다.
**그건 위 결정을 덮는 것이라 폐기한다.** 근거 있는 판정이 이미 도는데 그것을 지우고
내 구상으로 갈아엎을 이유가 없다. (MR !106 에서 같은 실수를 했다 — 명세에 적힌 것을
내 판단으로 덮었다.)

**확정: 조회 시점 파생 · 도메인별 포트 · 위에서부터 검사해 처음 참인 단계 반환.**

## 단계별 판정 근거

| 단계 | 근거 | 지금 |
| --- | --- | --- |
| `PRODUCT_LINKED` | `production_input` + `production_batch.status='CONFIRMED'` | ⏳ 엔티티 없음 |
| `PROCESSING_VERIFIED` | `processing_job.brand_reviewed_at` + `status='CONFIRMED'` | ⏳ 엔티티 없음 |
| `TRANSACTION_VERIFIED` | `material_trade.buyer_signed_at` | ✅ 판정 중 |
| `SOURCE_VERIFIED` | `blockchain_transaction` 의 `verifySource` 성공 Tx | 🆕 **이 티켓** |
| `REGISTERED` | 그 외 | ✅ 판정 중 |

### `SOURCE_VERIFIED` 만 온체인을 봐야 하는 이유

나머지 단은 **우리 서버를 반드시 거치는 흐름**의 부산물이다. 거래 수령, 가공 승인,
생산 확정은 모두 우리 API 를 통과하므로 그 사실이 DB 에 남는다.

`verifySource` 는 다르다. **최초 등록 SUPPLIER 가 "이 출처가 사실이다" 라고 서명하는
독립 행위**이고, 우리 흐름 어디에도 붙어 있지 않다. 대응하는 DB 사실이 없다.

v3 기획은 이 판정용으로 `material.signed_at` · `signer_wallet` 컬럼을 추가하려 했는데
실제 스키마에 없다(`08-divergences.md`). 그 컬럼을 지금 만들지 않는다 —
서명 사실은 온체인에 있고, DB 컬럼을 두면 두 개의 진실이 생긴다.

대신 **이미 있는 `blockchain_transaction`** 을 본다.

```
reference_type ENUM(... 'MATERIAL' ...)   어느 소재인지
reference_id   BIGINT
function_name  VARCHAR(100)               'verifySource'
status         ENUM('PENDING','SUCCESS','FAILED')
from_wallet    VARCHAR(42)                누가 서명했는지
confirmed_at   DATETIME                   언제
tx_hash        CHAR(66)                   어느 Tx 로
```

`status='SUCCESS'` 인 행만 인정한다. `PENDING` 은 아직 확정되지 않았고 `FAILED` 는
일어나지 않은 일이다. 둘 중 하나라도 인정하면 **일어나지 않은 검증을 표시하게 된다.**

이 테이블을 고른 또 하나의 이유는 **"누가·언제·어느 Tx" 가 공짜로 따라온다**는 것이다.
배지에 근거를 붙일 수 있다. 새 테이블도 새 컬럼도 필요 없다 — 스키마 변경 없이 끝난다.

### 오늘 이 판정이 정직한가 — 그렇다

`material.token_id` 가 전부 `NULL` 이다. mint 결과를 기록하는 confirm 엔드포인트가 없어서다.
**온체인에 없는 소재는 `verifySource` 대상이 될 수 없다.** 그래서 지금은 어떤 소재도
`SOURCE_VERIFIED` 로 올라가지 않으며, 그것이 사실이다. 낮게 보고하는 오류가 아니다.

## 남는 위험 — 과소 보고

`verifySource` 가 온체인에서 성공했는데 `blockchain_transaction` 에 행이 없으면
실제보다 낮게 보인다. 행을 쓰는 주체가 둘이다.

1. Tx 를 보내는 경로 — `PENDING` 으로 넣고 receipt 로 갱신 (`-153`, `-374` 대기)
2. 이벤트 리스너 `-205` — 우리 서버를 거치지 않은 직접 호출을 줍는다 (다른 담당자, 미착수)

2번이 없으면 사용자가 지갑으로 컨트랙트를 직접 호출한 경우를 놓친다.
**방향은 과소 보고**다 — 검증되지 않은 것을 검증됐다고 하지는 않는다. 추적성에서는
이 방향이 맞다. 반대 방향은 되돌릴 수 없는 거짓이 된다.

## 응답 형태

`verificationState` 는 그대로 두고 **근거를 옆에 붙인다.**

```json
"verificationState": "SOURCE_VERIFIED",
"verification": {
  "state": "SOURCE_VERIFIED",
  "signerWallet": "0x...",
  "signedAt": "2026-09-07T16:30:00",
  "txHash": "0x..."
}
```

근거가 없으면 `verification` 은 `null` 이다. 빈 객체를 내려보내지 않는다 —
화면이 "값이 있는데 비어 있음" 과 "아직 없음" 을 구분해야 한다.

`TRANSACTION_VERIFIED` 는 온체인 Tx 가 근거가 아니라 거래 서명이 근거이므로
`txHash` 가 `null` 일 수 있다. 그래서 필드를 `nullable` 로 둔다 — 단계마다 근거의
종류가 다르다는 사실을 응답이 숨기지 않게 한다.

## 구현 범위 (S15P21E104-434)

**이 티켓에서 하는 것 — 스키마 변경 없음**

- `BlockchainVerification` 포트를 material 에 선언, blockchain 패키지에서 구현
  (`TradeVerification` 과 같은 방식. blockchain 패키지는 지금 `package-info` 뿐이다)
- `BlockchainTransaction` 읽기 전용 엔티티 + 리포지토리 — 테이블은 이미 있다
- `MaterialVerificationStateResolver` 에 `SOURCE_VERIFIED` 단 추가.
  **`TRANSACTION_VERIFIED` 검사보다 아래**에 둔다 (위에서부터 검사하는 순서를 지킨다)
- 목록 조회는 소재 목록에 대해 한 번의 쿼리로 묶는다 (N+1 금지)
- 테스트: `SUCCESS` 만 인정 / `PENDING`·`FAILED` 무시 / `reference_type` 이 `MATERIAL`
  이 아니면 무시 / `function_name` 이 다르면 무시 / 거래 서명이 함께 있으면 더 높은
  `TRANSACTION_VERIFIED` 가 이긴다

**하지 않는 것과 그 이유**

- `PROCESSING_VERIFIED` — `processing_job` 스키마에 `brand_reviewed_at` 이 있지만
  **엔티티가 없다**(processing 은 견적 요청까지만 들어와 있다). 그 도메인이 합류할 때
  같은 포트 방식으로 자기 단을 얹는다
- `PRODUCT_LINKED` — `production` 패키지가 `package-info` 뿐이다. 게다가 온체인 호출
  경로가 `01-service.md` 에서 "예약" 으로 명시돼 있다
- 검증 Tx 를 *보내는* 경로 — `-153` 이 `-374`(컨트랙트 주소)로 막혀 있다

## 프론트엔드에 미치는 영향 (`-180`)

`material.types.ts` 의 **"⚠️ `verificationState` 를 신뢰하지 말 것"** 주석과
상세 화면의 "검증 단계 표시 준비 중" 자리표시자를 **지워야 한다.**
그 경고 자체가 잘못된 전제(항상 REGISTERED)에서 나왔다.

`VerificationBadge.tsx` 가 5단계 라벨·톤을 이미 갖고 있으므로 붙이기만 하면 된다.
별도 티켓으로 낸다 — 이 문서가 근거다.

## 2026-09-09 추가 — 검증 Tx 를 보내는 경로 (S15P21E104-171/-172)

위 "하지 않는 것" 목록의 `-153`/`-374` 블로킹은 낡은 정보였다. 컨트랙트 주소 env 키는
이미 세 곳(`.env.example`/`Dockerfile`/`contracts.ts`) 모두 `VITE_MATERIAL_CONTRACT_ADDRESS`
로 통일돼 있었고, mint 확정(`MaterialChainLinkService`)도 이미 배포돼 있었다.

**A안(`material.signed_at`/`signer_wallet` 컬럼 추가)은 다시 검토하지 않는다** — 위
"결정" 절이 이미 기각한 방안이고, 이 문서가 근거다. 대신 mint 확정과 같은 패턴으로
`MaterialSourceVerificationService`(`POST /api/materials/{id}/source-verification`)를
추가했다. 이 서비스는 **`material` 을 바꾸지 않는다** — `blockchain_transaction` 에
`verifySource` 행 하나를 남기고, `MaterialVerificationStateResolver` 는 그대로 둔다.

`onChainId` 를 받지 않는다 — mint 와 달리 verifySource 는 새로 채번되는 값이 없다.
등록자 본인만, 온체인에 없는(mint 안 된) 소재는 거절, 두 번 확정 거절 — 셋 다
`MaterialSourceVerificationApiTests` 가 고정한다.

`MaterialDetailResponse` 에 `verification` 필드를 추가했다(위 "응답 형태" 절 그대로).
`SOURCE_VERIFIED` 일 때만 `{state, signerWallet, signedAt, txHash}` 를 채우고, 다른 단은
근거의 모양이 달라(거래 서명 등) `null` 이다.

## 2026-09-09 추가 — 등록→발행 시나리오 테스트 (S15P21E104-440)

`-440`(`[TEST] Material 온체인 등록 테스트`) 검토 결과, 계층별 테스트는 이미 다 있었다.

- 컨트랙트 레벨 `mintMaterial`/`verifySource`/`verifyEvidence` — blockchain 패키지 컨트랙트
  테스트가 이미 고정
- mint 확정이 `material.token_id/chain_id/contract_address` 와
  `blockchain_transaction` 행을 남기는 것 — `ChainLinkApiTests.linksTheMintedTokenAndRecordsTheTransaction`
- 등록자 본인만 verifySource 확정 가능·미mint 소재 거절·중복 확정 거절·상세 조회의
  `SOURCE_VERIFIED` 전환 — `MaterialSourceVerificationApiTests` (컨트롤러 테스트, 위 절 참고)

빠진 것은 **하나의 시나리오로 이어 붙인 테스트**였다 — 개별 계층 테스트는 각자의
전제 상태(예: 이미 mint 된 소재)에서 시작해, 등록부터 시작하는 한 서사가 없었다.
`MaterialOnChainRegistrationScenarioTests`
(`src/test/java/com/ssafy/upcycle/material/MaterialOnChainRegistrationScenarioTests.java`)
를 추가해 **등록 → mint 확정 → verifySource 확정 → 상세 조회 하나**로 tokenId·chainId·
contractAddress·verificationState·verification 이 전부 일관되게 읽히는지 한 테스트
메서드로 검증한다. 기존 서비스·컨트롤러를 그대로 호출만 하며 재구현하지 않는다.

**여전히 막혀 있는 것**: `evidence_hash`/`manifest_url` 은 매니페스트 생성 경로가 없어
(`evidencePlaceholder.ts` 가 이를 자리표시자로 명시) 이 시나리오에서도 계속 `null` 이다.
이 티켓의 범위가 아니다 — Evidence 도메인(`-167`)이 별도로 다룬다.

## 2026-09-09 추가 — 같은 txHash 를 다른 소재에 재사용하는 것을 막는다

`MaterialSourceVerificationTxHashReuseApiTests` (MR !237, 티켓 없이 테스트 작업 중 발견)가
`MaterialSourceVerificationService.confirm` 의 빈 자리를 실행으로 고정해 두었다 — 같은
`txHash` 를 서로 다른 두 소재의 확정에 그대로 다시 보내면 **둘 다 200 으로 끝났다.**
서비스도 `blockchain_transaction` 의 유일한 유니크 제약(`(chain_id, tx_hash, log_index)`)도
이를 막지 않았다. 이 경로가 `log_index` 를 항상 `NULL` 로 남기고(위 "확정된 트랜잭션
하나만" 절 참고) MySQL 은 NULL 을 서로 다른 값으로 취급하므로 제약이 걸리지 않는다.

**이제 막는다.** 실제 블록체인 트랜잭션 해시는 온체인에서 한 번의 실행에만 대응한다 —
하나의 `verifySource` 트랜잭션은 그 트랜잭션이 emit 한 `SourceVerified` 이벤트의 정확히
하나의 `(tokenId, signer, evidenceHash)` 삼중값에만 대응할 수 있다. 같은 해시가 서로
**다른** 소재의 확정에 정당하게 재사용될 시나리오는 없다 — 위조이거나 클라이언트 버그다.

**어디서 막는가.** `MaterialSourceVerificationService.confirm` 안, 기존 "이미 검증됨"
검사 바로 다음이다. `BlockchainTransactionRepository` 에 조건을 하나 더 추가했다 —
`existsByReferenceTypeAndFunctionNameAndTxHashAndStatusAndReferenceIdNot`. 같은 txHash 가
**다른** `reference_id` 에 이미 `SUCCESS` 로 있으면 새 에러 코드
`MATERIAL_SOURCE_VERIFICATION_TX_HASH_REUSED` (409) 로 거절한다. 같은 소재를 다시
확정하는 경우는 이 검사를 타지 않는다 — 그건 이미 `MATERIAL_SOURCE_ALREADY_VERIFIED` 가
막는다.

**공유 `ChainTransactionRecorder` 가 아니라 여기(`MaterialSourceVerificationService`)에만
좁혀서 고쳤다.** mint 확정(`MaterialChainLinkService`)·판매 등록·거래 확정에도 이론상
같은 종류의 구멍이 있다 — 예: mint 확정도 `material.token_id != null` 여부만 보고 같은
txHash 가 다른 소재의 mint 로 이미 쓰였는지는 안 본다. 하지만 그 확인은 이 티켓의
범위가 아니다:

- 이 수정은 지금 **실행으로 고정된 실패 테스트 하나**를 닫는 것이 목적이다. 다른 흐름은
  아직 그런 테스트가 없어 회귀 여부를 검증할 고정점이 없다.
- mint·판매·거래 확정은 각각 `material.token_id`, 판매글의 체인 등록 상태, 거래의
  체인 확정 상태처럼 **참조 대상 엔티티가 이미 재배정을 막는** 별도 가드가 있다
  (`MaterialChainLinkService` 주석 참고). 그 가드가 이 재사용까지 완전히 막는지는
  각 흐름을 개별적으로 검토해야 하고, `ChainTransactionRecorder.record` 수준에서
  일괄로 막으면 세 흐름의 동작을 한 번에 바꾸는 것이라 회귀 위험이 이 티켓의 범위보다
  크다.
- 필요하면 별도 티켓으로 mint/판매/거래 각각에 대해 같은 방식(도메인 서비스의 confirm
  안에서 `referenceIdNot` 대조)으로 좁혀서 따로 고치는 편이 안전하다.

`MaterialSourceVerificationTxHashReuseApiTests` 는 더 이상 빈 자리를 문서화하지 않는다 —
이제는 이 방지 로직이 실제로 동작한다는 보장을 고정한다. 테스트 메서드 이름도
`currentlyAllowsTheSameTxHashAcrossDifferentMaterials` 에서
`rejectsTheSameTxHashAcrossDifferentMaterials` 로 바꿨다.
