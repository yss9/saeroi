## 1. 전체 관계도

```
users ──┬── company_profile
        ├── processor_profile ──< processor_capability
        │                     └──< processor_lead_time
        ├── consumer_address
        ├── user_wallet
        └──< material_balance >── material
                                     │
                    ┌────────────────┼────────────────┐
                    │                │                │
              material_lock    material_evidence   material_lineage
                                                        ▲
        ┌───────────────────────────────────────────────┤
        │                                               │
  processing_job ──< processing_input               production_batch
        │        └──< processing_output                  │
        │                                          ┌─────┴─────┐
  processing_request ──< processing_quote     production_input  production_output
        └──< processing_request_input                │
        └──< processing_request_type            product_item ──> product_model
                                                     │
                                        product_order_item >── product_order
                                                                     │
                                                              product_shipping

material_listing ──< material_trade ──┬── trade_shipping
                                      └── trade_dispute

blockchain_transaction   evidence_file   point_account ──< point_transaction
```

# 공통 상태 확정 정책 — 2-Track

플랫폼 내 사용자 간 물품·가공 결과 등의 상태 변경은 원칙적으로 **보낸 측과 받은 측의 2단계 확인 방식**으로 처리한다.

```
보낸 측
요청 / 제출 / 배송
        ↓
받은 측
수령 / 검토
        ↓
승인 또는 반려
        ↓
최종 상태 확정
```

### 기본 원칙

1. 보낸 측은 자신의 행위를 직접 기록한다.
2. 받은 측은 상대방이 기록한 내용을 수령·검토한 뒤 승인 또는 반려한다.
3. 상대방의 확인 이전에는 최종 완료 상태로 전환하지 않는다.
4. 이미 제출된 기록은 수정하지 않는다.
5. 반려된 건은 해당 기록을 종료하고, 필요한 경우 새로운 절차를 생성한다.
6. Blockchain 기록 대상 행위는 각각 독립된 Transaction으로 남긴다.
7. 반려 역시 하나의 검증 결과이므로 필요한 도메인에서는 Blockchain Transaction으로 기록한다.

### 도메인별 적용

| 도메인 | 보낸 측 | 받은 측 | 최종 확정 |
| --- | --- | --- | --- |
| Material 거래 | Seller 배송 | Buyer 수령 확인 | Material 소유권 이전 |
| Processing 소재 전달 | Brand 배송 | Processor 수령 | 가공 시작 가능 |
| Processing 결과 | Processor 결과 제출 | Brand 승인/반려 | 가공 결과 확정 또는 반려 |
| Product 주문 | Brand 배송 | Consumer 수령 확인 | 주문 완료 |
| Production | Brand 생산 결과 등록 | 동일 Brand 확정 | 생산 결과 확정 |

### 담당

| 도메인 | 테이블 | 담당 |
| --- | --- | --- |
| User / Wallet | users, company_profile, consumer_address, user_wallet, wallet_nonce, refresh_token | 6번 |
| Material | material, material_balance, material_lock, material_lineage | 2번 |
| Marketplace | material_listing, material_trade, trade_shipping, trade_dispute | 3번 |
| Processing | processing_request(+input/type), processing_quote, processing_job(+input/output), processor_profile 계열 | 4번 |
| Production | production_batch, production_input, production_output | 5번 |
| Product | product_model, product_item, product_order, product_order_item, product_shipping | 5번 |
| 공통 | blockchain_transaction, evidence_file, point_account, point_transaction | 6번 |

---

## 2. User 영역

### users

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK |  |
| email | VARCHAR(255) UNIQUE |  |
| password | VARCHAR(255) | BCrypt |
| name | VARCHAR(100) |  |
| phone | VARCHAR(20) |  |
| role | ENUM | SUPPLIER / BRAND / PROCESSOR / CONSUMER / ADMIN |
| status | ENUM | ACTIVE / SUSPENDED |
| created_at | DATETIME |  |

Wallet 주소를 여기 두지 않는다. 지갑 교체 시 이전 주소 이력이 사라지기 때문이다.

### user_wallet

SUPPLIER / BRAND / PROCESSOR 만 갖는다. CONSUMER 는 레코드가 없다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK |  |
| user_id | BIGINT FK UNIQUE |  |
| wallet_address | VARCHAR(42) UNIQUE | 0x72A... |
| chain_id | BIGINT |  |
| verified | BOOLEAN |  |
| verified_at | DATETIME | nonce 서명 검증 완료 시각 |
| created_at | DATETIME |  |

### wallet_nonce

지갑 소유 증명용. 주소만 입력받으면 남의 주소를 등록할 수 있다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK |  |
| user_id | BIGINT FK |  |
| wallet_address | VARCHAR(42) |  |
| chain_id | BIGINT |  |
| nonce | CHAR(32) | 일회용 랜덤값 |
| expires_at | DATETIME |  |
| used_at | DATETIME NULL | 재사용 방지 |

서버가 nonce 발급 → 사용자가 MetaMask 서명 → 서버가 `ecrecover` 로 주소 복원 → 일치 확인.

### company_profile / consumer_address / refresh_token

| company_profile (1:1, 사업자 3종) |  |
| --- | --- |
| user_id | BIGINT FK UNIQUE |
| company_name, business_number | VARCHAR |
| zip_code, address, address_detail | VARCHAR |
| introduction, logo_url | TEXT / VARCHAR |

| consumer_address (1:N, 소비자) |  |
| --- | --- |
| id, user_id | BIGINT |
| alias, receiver_name, receiver_phone | VARCHAR |
| zip_code, address, address_detail | VARCHAR |
| is_default | BOOLEAN |

| refresh_token |  |
| --- | --- |
| id, user_id | BIGINT |
| token | VARCHAR(500) UNIQUE |
| expires_at, revoked_at, created_at | DATETIME |

### processor_profile / processor_capability / processor_lead_time

| processor_profile (1:1) |  |
| --- | --- |
| user_id | BIGINT FK UNIQUE |
| region | VARCHAR(100) |
| min_quantity, max_quantity | BIGINT (g) |

| processor_capability (1:N) |  |
| --- | --- |
| processor_profile_id | BIGINT FK |
| material_type | ENUM |
| process_type | ENUM (8종) |

UNIQUE `(processor_profile_id, material_type, process_type)`

| processor_lead_time (1:N) |  |
| --- | --- |
| processor_profile_id | BIGINT FK |
| max_quantity | BIGINT — 이 수량 이하일 때 |
| lead_days | INT |

업체 검색이 `processor_capability` 조인 + `min/max_quantity` 필터로 이루어진다.

**process_type ENUM**`CLEANING / DISASSEMBLY / SORTING / CUTTING / SHREDDING / COLOR_PROCESSING / SURFACE_FINISHING / REMANUFACTURING`

---

## 3. Material 영역

### material

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK |  |
| material_code | VARCHAR(20) UNIQUE | MAT-000001 |
| material_type | ENUM | DENIM / COTTON / LINING / METAL / ... |
| **root_origin** | ENUM | VIRGIN / WASTE / RECOVERED |
| **generation_type** | ENUM | INITIAL / PROCESSED / PRODUCTION_SCRAP |
| name | VARCHAR(200) | 세척된 데님 |
| description | TEXT |  |
| grade | VARCHAR(50) NULL | 등급·품질 표기 |
| total_quantity | BIGINT | 총 발행 수량 (g) |
| length_mm / width_mm | BIGINT NULL | 참고 Metadata |
| verification_state | ENUM | REGISTERED / SOURCE_VERIFIED / TRANSACTION_VERIFIED / PROCESSING_VERIFIED / PRODUCT_LINKED |
| registrant_id | BIGINT FK | 최초 등록자 |
| status | ENUM | ACTIVE / DEPLETED |
| created_at | DATETIME |  |

**`parent_material_id` 컬럼이 없다.** 혈통은 `material_lineage` 로 표현한다. (9장)

**`root_origin` / `generation_type` 을 분리한 이유**

기존 `origin_type` 은 출처(VIRGIN, WASTE)와 상태(PROCESSED)를 한 컬럼에 섞고 있었다.
차원이 다르므로 같은 축에 놓을 수 없다.

```
MAT-001   root_origin=WASTE   generation_type=INITIAL
   ↓ 세척
MAT-002   root_origin=WASTE   generation_type=PROCESSED    ← 업사이클 계열
```

```
MAT-100   root_origin=VIRGIN  generation_type=INITIAL
   ↓ 염색
MAT-101   root_origin=VIRGIN  generation_type=PROCESSED    ← 새 원단 계열
```

`root_origin` 은 자식 생성 시 부모에서 그대로 복사한다.
업사이클 비율은 이 값 하나로 판단하며, 매번 혈통 트리를 재귀 조회할 필요가 없다.

투입 자격 검증도 단순해진다.

```
Production 투입 불가:  root_origin = 'WASTE' AND generation_type = 'INITIAL'
```

**`grade` 를 두는 이유** — 하나의 Material ID 는 물성·등급·출처가 동일해 어느 부분을 떼어가도
같게 취급 가능한 하나의 Batch 를 의미한다. Balance 방식이 이 동질성을 전제하기 때문이다.
품질이 다르면 처음부터 별도 Material 로 등록한다.

### material_balance

DB가 Material Balance의 Source of Truth.
Blockchain은 거래·가공·생산에 대한 검증 이력 저장.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK |  |
| material_id | BIGINT FK |  |
| owner_id | BIGINT FK |  |
| wallet_address | VARCHAR(42) | 조회 편의 |
| quantity | BIGINT | 보유량 (g) |

UNIQUE `(material_id, owner_id)`

```
available = quantity − SUM(material_lock.amount WHERE status = 'ACTIVE')
```

Lock 합계를 컬럼으로 들고 있지 않는다. 중복 저장하면 반드시 어긋난다.

### material_lock

Lock 마다 ID 를 부여한다. 합계 컬럼만 두면 해제·만료·추적을 구현할 수 없다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK |  |
| material_id | BIGINT FK |  |
| owner_id | BIGINT FK | 잠긴 주체 |
| amount | BIGINT | 잠금 수량 (g) |
| purpose | ENUM | MARKETPLACE / PROCESSING / PRODUCTION |
| reference_type | ENUM | LISTING / TRADE / PROCESSING_JOB / PRODUCTION_BATCH |
| reference_id | BIGINT | 해당 도메인 PK |
| status | ENUM | ACTIVE / RELEASED / CONSUMED |
| expires_at | DATETIME NULL | 만료 기준 |
| created_at / released_at | DATETIME |  |

INDEX `(material_id, owner_id, status)`, `(reference_type, reference_id)`

| 상태 | 의미 |
| --- | --- |
| ACTIVE | 잠금 중, `available` 에서 차감 |
| RELEASED | 취소·실패로 해제, 수량 복귀 |
| CONSUMED | 실제 소비 완료, `quantity` 자체가 감소 |

**Lock 생성·해제 시점**

| 도메인 | 생성 | 해제(RELEASED) | 소비(CONSUMED) |
| --- | --- | --- | --- |
| Marketplace | 판매 등록 | 판매 취소 | Buyer 수령 확인 |
| Processing | **견적 선택** | 거래 취소 | Processing 확정 |
| Production | DRAFT 저장 | 배치 취소·FAILED 만료 | Production 확정 |

견적 요청 단계에서는 Lock 하지 않는다. 견적 선택 시점부터다.

**배송 중 Lock 유지가 중요하다.** Seller 가 배송 중인 20kg 을 다른 곳에 또 팔 수 있으면 안 된다.
Marketplace Lock 은 Buyer 수령 확인까지 유지되고, 그 시점에 CONSUMED 로 바뀌며 Balance 가 이전된다.

**부분 판매 시 Lock 처리**

Material Listing이 부분 구매를 지원하므로 거래 완료 수량만큼
Listing에 대응하는 material_lock.amount를 감소시킨다.

예:
Listing 30kg → Lock 30kg
10kg 거래 완료 → Lock 20kg

잔여 판매 수량이 0이 되면 해당 Lock을 CONSUMED 처리한다.

### material_lineage

혈통 간선. Processing 과 Production 이 확정될 때 생성한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK |  |
| child_material_id | BIGINT FK |  |
| parent_material_id | BIGINT FK |  |
| relation_type | ENUM | PROCESSING / PRODUCTION_SCRAP |
| reference_id | BIGINT | processing_job_id 또는 production_batch_id |
| created_at | DATETIME |  |

UNIQUE `(child_material_id, parent_material_id, reference_id)`
INDEX `child_material_id`, `parent_material_id`

**왜 이 테이블이 필요한가**

Material 에 `parent_material_id` 컬럼 하나만 두면 1:N 은 되지만 N:1 이 안 된다.

```
1 → N   해체
MAT-001 → MAT-002(데님) / MAT-003(안감) / MAT-004(금속)
```

정책 9장의 `SHREDDING`, `REMANUFACTURING` 이 있는 이상 N:1 은 반드시 발생한다.

혈통의 진실은 `processing_input` / `processing_output` 이지만,
Journey 를 한 단계씩 거슬러 올라갈 때마다 Processing 과 Production 을 각각 조회하면
6번 담당이 도메인마다 다른 쿼리를 써야 한다.
이 테이블은 두 경로를 하나의 간선 형태로 통일해주는 조회용 구조다.

### evidence_file

증빙은 도메인마다 여러 개 생긴다. 하나의 공통 테이블로 관리한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK |  |
| reference_type | ENUM | MATERIAL / PROCESSING_JOB / PRODUCTION_BATCH / PRODUCTION_INPUT / TRADE_DISPUTE |
| reference_id | BIGINT |  |
| file_url | VARCHAR(500) | Object Storage |
| file_hash | CHAR(66) | 개별 파일 SHA-256 |
| evidence_type | ENUM | BEFORE_PHOTO / AFTER_PHOTO / WEIGHT_SLIP / INVOICE / PROCESS_PHOTO / ETC |
| uploaded_at | DATETIME |  |

INDEX `(reference_type, reference_id)`

**Evidence Manifest 방식**

파일 하나마다 온체인 해시를 남기면 Processing 한 건에 5개씩 들어간다.
파일 목록을 담은 manifest JSON 을 만들어 그 해시 하나만 체인에 기록한다.

json

```json
{ "evidences": [ { "url": "...", "hash": "0x..." }, ... ] }     ↓ SHA-256evidence_hash  → processing_job.evidence_hash (온체인 기록)
```

파일 개수가 늘어도 컨트랙트는 `bytes32` 하나만 관리한다.

## 4. Processing 영역

견적 방식이므로 **요청 → 견적 → 작업** 3단계다.

### processing_request

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK |  |
| request_code | VARCHAR(30) UNIQUE |  |
| brand_id | BIGINT FK |  |
| status | ENUM | QUOTING / SELECTED / CANCELED / EXPIRED |
| expires_at | DATETIME | 견적 마감 |
| created_at | DATETIME |  |
| canceled_at | DATETIME |  |
| cancel_reason | VARCHAR(255) NULL |  |

### processing_request_input

하나의 가공 요청에 여러 소재를 함께 요청할 수 있으므로 1:N으로 관리한다. 단, 서로 다른 Material을 혼합하여 하나의 새로운 Material을 생성하는 가공은 지원하지 않는다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK |  |
| processing_request_id | BIGINT FK |  |
| material_id | BIGINT FK |  |
| quantity | BIGINT | 요청 수량 (g) |

### processing_request_type

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK |  |
| processing_request_id | BIGINT FK |  |
| process_type | ENUM | 세척 + 해체처럼 복수 지정 가능 |

### processing_quote

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK |  |
| processing_request_id | BIGINT FK |  |
| processor_id | BIGINT FK |  |
| price | BIGINT | 견적가 (P) |
| lead_days | INT | 예상 소요일 |
| memo | TEXT |  |
| status | ENUM | SUBMITTED / SELECTED / REJECTED / EXPIRED |
| created_at | DATETIME |  |
|  |  |  |

UNIQUE `(processing_request_id, processor_id)` — 한 업체가 같은 요청에 견적 하나

### processing_job

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK |  |
| job_code | VARCHAR(30) UNIQUE |  |
| processing_request_id | BIGINT FK |  |
| processing_quote_id | BIGINT FK UNIQUE |  |
| brand_id / processor_id | BIGINT FK |  |
| brand_wallet / processor_wallet | VARCHAR(42) | 서명 시점 스냅샷 |
| fee | BIGINT | 가공비 (P) |
| loss_quantity | BIGINT | **계산값** (아래 설명) |
| evidence_hash | CHAR(66) NULL | Manifest 해시 |
| status | ENUM | 아래 참조 |
| rejection_reason | TEXT NULL |  |
| processor_submitted_at | DATETIME NULL | Processor 서명 Tx |
| brand_approved_at | DATETIME NULL | Brand 승인 Tx |
| brand_rejected_at | DATETIME NULL | Brand 반려 Tx |
| created_at / shipped_at / received_at / completed_at | DATETIME |  |

**하나의 Processing Job은 하나의 원본 Material ID만을 INPUT으로 가진다.**

**status 흐름**

```
PAID
 ↓
SHIPPED
 ↓
RECEIVED
 ↓
PROCESSING
 ↓
RESULT_SUBMITTED
 ↓
┌──────────────┐
CONFIRMED   REJECTED
```

| 상태 | 의미 | 주체 |
| --- | --- | --- |
| PAID | 가공비 결제·Escrow | BRAND |
| SHIPPED | Material 발송 | BRAND |
| RECEIVED | Processor 수령 | PROCESSOR |
| PROCESSING | 가공 중 | PROCESSOR |
| RESULT_SUBMITTED | 결과·증빙 등록 + Wallet 서명 | PROCESSOR |
| CONFIRMED | Brand 승인 → 온체인 확정 → Child Material 생성 → 정산 | BRAND |
| REJECTED | Brand가 제출 결과를 반려한 종료 상태 | BRAND |

REJECTED 상태에서는 기존 결과를 수정하거나 재제출하지 않는다.
재가공 또는 재처리가 필요한 경우 새로운 Processing Job을 생성한다.

가공 시작 전 취소 → RELEASED
가공 결과 승인 → CONSUMED
가공 결과 반려 → 별도 실패 처리 후 수량 상태 결정

**양측 서명 2단 확정**

누가 최종 Tx 를 보내는지 애매하지 않도록 각자 트랜잭션을 보낸다.

```
**2-Track 온체인 기록**

Processor와 Brand는 각각 자신의 행위를 별도 Transaction으로 기록한다.

PROCESSOR
submitProcessingResult()
→ processor_submitted_at

BRAND 승인
approveProcessingResult()
→ brand_approved_at

또는

BRAND 반려
rejectProcessingResult()
→ brand_rejected_at

                    ↓
Processor 결과 제출 후 Brand가 승인한 경우에만
Processing Job을 CONFIRMED 처리하고 Child Material을 생성한다.

Brand가 반려하면 Processing Job은 REJECTED로 종료하며
Child Material은 생성하지 않는다.
```

Brand 가 대신 제출하면 Processor 서명 사실을 체인에서 증명할 방법을 따로 만들어야 한다.

**소유권은 계속 Brand 다.** Processor 가 물리적으로 보관할 뿐 Balance 는 이동하지 않는다.

### processing_input / processing_output

| processing_input | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK |  |
| processing_job_id | BIGINT FK |  |
| material_id | BIGINT FK |  |
| quantity | BIGINT | 실제 투입량 (g) |
| material_lock_id | BIGINT FK | 대응 Lock |

| processing_output | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK |  |
| processing_job_id | BIGINT FK |  |
| output_material_id | BIGINT FK NULL | 확정 후 채워짐 |
| material_type | ENUM | DENIM / LINING / METAL |
| name | VARCHAR(200) |  |
| quantity | BIGINT | 산출량 (g) |

`output_material_id` 가 NULL 인 이유: `RESULT_SUBMITTED` 시점에는 온체인 확정 전이라
Child Material 이 아직 없다. `CONFIRMED` 에서 생성 후 연결한다.

Brand가 REJECTED 처리한 경우 output_material_id는 NULL 상태로 유지하며
해당 Processing Output을 수정하지 않는다.

**요청 수량과 실제 투입량은 다를 수 있다.** `processing_request_input` 과 `processing_input` 을
분리한 이유다. 20kg 요청했는데 계근하니 20.03kg 인 경우가 실제로 생긴다.

### Loss 는 입력받지 않고 계산한다

```
loss_quantity = SUM(processing_input.quantity) − SUM(processing_output.quantity)
```

Processor 가 Loss 를 직접 입력하고 `input == outputs + loss` 를 검증하는 방식은
계근 오차·수분 증발 때문에 실제 업무에서 자주 막힌다.
Loss 를 계산값으로 두면 검증식이 항상 성립하고, Processor 는 실측값만 넣으면 된다.

음수 방지만 검증한다.

```
SUM(outputs) <= SUM(inputs)
```

---

## 5. Production 영역

### production_batch

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK |  |
| batch_code | VARCHAR(20) UNIQUE | PB-000001 |
| batch_name | VARCHAR(200) | RE:BAG 1차 생산 |
| product_model_id | BIGINT FK |  |
| produced_quantity | INT | 생산 개수 |
| producer_id | BIGINT FK | BRAND |
| producer_wallet | VARCHAR(42) | 서명 시점 스냅샷 |
| status | ENUM | DRAFT / SIGNING / CONFIRMED / FAILED / CANCELED |
| total_used_quantity | BIGINT | 제품 사용 총량 (g) |
| virgin_used_quantity | BIGINT | VIRGIN 계열 사용량 |
| upcycled_used_quantity | BIGINT | WASTE·RECOVERED 계열 사용량 |
| upcycle_ratio | DECIMAL(5,2) | 계산 결과 스냅샷 |
| evidence_hash | CHAR(66) NULL | Manifest 해시 |
| created_at / confirmed_at | DATETIME |  |
| canceled_at | DATETIME |  |
| cancel_reason | VARCHAR(255) NULL |  |
| failed_at | DATETIME |  |
| failure_reason | VARCHAR(255) NULL |  |

**업사이클 비율 계산**

```
upcycled = Σ product_used_quantity WHERE material.root_origin IN ('WASTE','RECOVERED')
virgin   = Σ product_used_quantity WHERE material.root_origin = 'VIRGIN'
upcycle_ratio = upcycled / (virgin + upcycled) × 100
```

`root_origin` 이 분리되어 있으므로 혈통 재귀 없이 한 번의 조인으로 끝난다.

**스냅샷으로 저장한다.** 매번 재계산하면 나중에 소재 데이터가 바뀔 때
이미 팔린 제품의 비율이 소급해 변한다. 그건 이력이 아니다.

### production_input

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK |  |
| production_batch_id | BIGINT FK |  |
| material_id | BIGINT FK |  |
| input_quantity | BIGINT | 투입량 (g) |
| product_used_quantity | BIGINT | 제품 사용 (g) |
| reusable_scrap_quantity | BIGINT | 재사용 자투리 (g) |
| waste_quantity | BIGINT | 폐기 (g) |
| waste_reason | VARCHAR(200) NULL |  |
| material_lock_id | BIGINT FK | 대응 Lock |

UNIQUE `(production_batch_id, material_id)` — 같은 소재 중복 투입 방지
INDEX `material_id` — Journey 역추적

**검증식**

```
input_quantity = product_used_quantity + reusable_scrap_quantity + waste_quantity
```

행마다 검증한다. 배치 합계로만 보면 한 소재를 과다 입력하고 다른 소재를 과소 입력해도 통과한다.

**투입 자격**

```
NOT (material.root_origin = 'WASTE' AND material.generation_type = 'INITIAL')
```

폐소재는 Processing 을 최소 1회 거쳐야 한다.

### production_output

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK |  |
| production_input_id | BIGINT FK |  |
| new_material_id | BIGINT FK NULL | 확정 후 채워짐 |
| quantity | BIGINT | 재발행 수량 (g) |

새 Material 은 `generation_type = PRODUCTION_SCRAP`, `root_origin = 부모에서 복사`.
동시에 `material_lineage` 간선을 생성한다.

---

## 6. Product 영역

### product_model

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK |  |
| name | VARCHAR(200) | RE:BAG |
| category | ENUM | BAG / WALLET / CLOTHING / ... |
| brand_id | BIGINT FK |  |
| price | BIGINT | 판매가 (P) |
| description | TEXT |  |
| thumbnail_url | VARCHAR(500) |  |
| status | ENUM | DRAFT / ON_SALE / SOLD_OUT / DISCONTINUED |
| created_at | DATETIME |  |

**재고 컬럼을 두지 않는다.** `product_item` 을 status 로 COUNT 한다.
별도 컬럼을 두면 판매 처리 중 한쪽만 실패했을 때 화면 재고와 실제가 조용히 어긋난다.

### product_item

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK |  |
| production_batch_id | BIGINT FK |  |
| product_model_id | BIGINT FK | 조회 편의 |
| serial_number | INT | 배치 내 순번 |
| item_code | VARCHAR(50) UNIQUE | RE:BAG #001 |
| qr_token | CHAR(32) UNIQUE | 추측 불가 랜덤값 |
| status | ENUM | IN_STOCK / RESERVED / SOLD |
| created_at | DATETIME |  |

INDEX `(product_model_id, status)` — 재고 COUNT

`qr_token` 을 id 와 분리하는 이유: QR URL 에 순번이 들어가면 URL 조작으로
남의 제품 이력을 전부 열람할 수 있다.

**추적 수준** — 하나의 ProductionBatch 에서 나온 ProductItem 은 동일 Composition 으로 간주한다.
"이 가방에 바로 MAT-001 500g 이 들어갔다" 를 증명하는 것이 아니라
"이 개체는 이 Batch 에서 생산됐고 그 Batch 에 이 소재들이 이 비율로 사용됐다" 를 보장한다.
QR 화면 문구도 이 수준으로 표기한다.

### product_order / product_order_item / product_shipping

| product_order | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK |  |
| order_code | VARCHAR(30) UNIQUE |  |
| buyer_id / brand_id | BIGINT FK |  |
| total_price | BIGINT | 스냅샷 |
| status | ENUM | CREATED / PAID / SHIPPED / DELIVERED |
| payment_method | ENUM | POINT / MOCK |
| payment_reference | VARCHAR(100) |  |
| payment_deadline | DATETIME | 생성 + 30분 |
| created_at / paid_at / shipped_at / delivered_at | DATETIME |  |

```
**Product 배송 2-Track**

BRAND
배송 처리
→ SHIPPED

CONSUMER
수령 확인
→ DELIVERED

Brand의 배송 처리만으로 주문을 완료하지 않으며,
Consumer의 수령 확인 시 최종 완료 처리한다.

취소 처리
→ canceled_product_order로 이동
→ product_order 삭제
```

| product_order_item | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK |  |
| product_order_id | BIGINT FK |  |
| product_item_id | BIGINT FK **UNIQUE** |  |
| product_model_id | BIGINT FK |  |
| price | BIGINT | 주문 시점 단가 |

**`product_item_id` UNIQUE 가 핵심이다.** 같은 개체가 두 주문에 들어가는 것을 DB 가 막는다.
동시 구매 방지 로직에 구멍이 있어도 이 제약이 최후 방어선이 된다.

| product_shipping | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK |  |
| product_order_id | BIGINT FK UNIQUE |  |
| receiver_name / receiver_phone | VARCHAR |  |
| zip_code / address / address_detail | VARCHAR |  |
| carrier / tracking_number | VARCHAR NULL |  |
| shipped_at | DATETIME NULL |  |

Consumer 는 Wallet 을 사용하지 않으므로 제품 주문에는 서명도 온체인 기록도 없다.

## canceled_product_order

취소된 주문의 이력을 별도로 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK | 취소 주문 이력 ID |
| original_order_id | BIGINT | 기존 product_order PK |
| order_code | VARCHAR(30) | 기존 주문 코드 |
| buyer_id | BIGINT FK | 구매자 |
| brand_id | BIGINT FK | 판매 Brand |
| total_price | BIGINT | 주문 당시 총 결제 금액 |
| payment_method | ENUM | POINT / MOCK |
| payment_reference | VARCHAR(100) NULL | 결제 참조값 |
| cancel_reason | VARCHAR(255) NULL | 취소 사유 |
| original_created_at | DATETIME | 최초 주문 생성 시각 |
| canceled_at | DATETIME | 취소 시각 |

## canceled_product_order_item

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK |  |
| canceled_product_order_id | BIGINT FK | 취소 주문 |
| original_order_item_id | BIGINT | 기존 product_order_item PK |
| product_item_id | BIGINT FK | 취소된 ProductItem |
| product_model_id | BIGINT FK |  |
| price | BIGINT | 주문 당시 단가 |

취소 처리는 반드시 하나의 DB Transaction으로 수행한다.

주문 취소 시 활성 주문 테이블에서
product_order 및 product_order_item을 제거하고
취소 주문 이력 테이블로 이동한다.

이를 통해 product_order_item.product_item_id UNIQUE 제약을 유지하면서
취소된 ProductItem을 다시 판매할 수 있다.

```
1. canceled_product_order 저장
2. canceled_product_order_item 저장
3. product_order_item 삭제
4. product_order 삭제
5. product_item.status → IN_STOCK
6. Point 결제/Lock이 존재하면 환불 또는 Lock 해제
```

## 7. Marketplace / Trade 영역

정책상 우선순위가 낮은 추가 기능이다.

### material_listing

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK |  |
| material_id / seller_id | BIGINT FK |  |
| total_quantity / available_quantity | BIGINT | 부분 구매 지원 |
| price_per_kg | BIGINT | (P) |
| material_lock_id | BIGINT FK | 등록 시 생성된 Lock |
| status | ENUM | ON_SALE / SOLD_OUT / CANCELED |
| canceled_at | DATETIME |  |
| cancel_reason | VARCHAR(255) NULL |  |

### material_trade

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK |  |
| trade_code | VARCHAR(30) UNIQUE |  |
| listing_id / material_id | BIGINT FK |  |
| seller_id / buyer_id | BIGINT FK |  |
| seller_wallet / buyer_wallet | VARCHAR(42) | 스냅샷 |
| quantity | BIGINT | (g) |
| total_price | BIGINT | (P) |
| status | ENUM | CREATED / PAID / SHIPPED / RECEIVED / COMPLETED / CANCELED / DISPUTED |
| created_at / paid_at / shipped_at / received_at / completed_at / canceled_at | DATETIME |  |
| cancel_reason | VARCHAR(255) NULL |  |

Buyer 수령 확인 + Wallet 서명 시점에 Balance 가 이전되고 Lock 이 CONSUMED 로 바뀐다.

### trade_shipping / trade_dispute

| trade_shipping | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK | 배송 정보 식별자 |
| trade_id | BIGINT FK UNIQUE | 대상 Material 거래 |
| receiver_name | VARCHAR(100) | 수령인 이름 |
| receiver_phone | VARCHAR(20) | 수령인 연락처 |
| zip_code | VARCHAR(20) | 우편번호 |
| address | VARCHAR(255) | 배송 주소 |
| address_detail | VARCHAR(255) NULL | 상세 주소 |
| carrier | VARCHAR(50) NULL | 택배사 |
| tracking_number | VARCHAR(100) NULL | 운송장 번호 |
| shipped_at | DATETIME NULL | Seller 배송 처리 시각 |
| delivered_at | DATETIME NULL | 실제 배송 완료 시각 또는 수령 처리 시각 |

| trade_dispute | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK |  |
| trade_id | BIGINT FK |  |
| raised_by | BIGINT FK |  |
| reason_type | ENUM | NOT_DELIVERED / QUANTITY_MISMATCH / CONDITION_MISMATCH |
| description | TEXT |  |
| status | ENUM | OPEN / RESOLVED |
| resolution | ENUM NULL | BUYER_WIN / SELLER_WIN |
| admin_id | BIGINT FK NULL |  |
| resolved_at | DATETIME NULL |  |

부분 환불은 MVP 미지원이므로 결과가 두 가지뿐이다. 증빙은 `evidence_file` 로 관리한다.

---

## 8. 공통 영역

### blockchain_transaction

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK |  |
| tx_hash | CHAR(66) |  |
| reference_type | ENUM | MATERIAL / TRADE / PROCESSING / PRODUCTION / DISPUTE |
| reference_id | BIGINT |  |
| chain_id | BIGINT |  |
| contract_address | VARCHAR(42) |  |
| function_name | VARCHAR(100) | approveProcessingResult |
| from_wallet | VARCHAR(42) |  |
| status | ENUM | PENDING / SUCCESS / FAILED |
| block_number | BIGINT NULL |  |
| error_message | TEXT NULL |  |
| created_at / confirmed_at | DATETIME |  |

하나의 도메인 작업에서 여러 Blockchain Transaction이 발생할 수 있다.

예:

Processing Job #100

- submitProcessingResult
- approveProcessingResult

또는

- submitProcessingResult
- rejectProcessingResult

따라서 각 도메인 테이블에 단일 tx_hash를 저장하지 않고
blockchain_transaction에서 1:N으로 관리한다.

INDEX `(reference_type, reference_id)`, `tx_hash`

**각 도메인 테이블에 tx_hash 컬럼을 두지 않는다.** 재시도하면 Tx 가 여러 건 생기는데
단일 컬럼이면 이력이 덮어써진다. Processing 은 Processor·Brand 두 건이 생기므로 특히 그렇다.

### point_account / point_transaction

| point_account | 타입 |
| --- | --- |
| id, user_id (UNIQUE FK) | BIGINT |
| balance, locked | BIGINT |

| point_transaction | 타입 |
| --- | --- |
| id, user_id | BIGINT |
| amount | BIGINT (양수 입금 / 음수 출금) |
| type | ENUM: CHARGE / TRADE_PAY / TRADE_SETTLE / PROCESSING_PAY / PROCESSING_SETTLE / PRODUCT_PAY / REFUND |
| reference_type, reference_id |  |
| created_at | DATETIME |

결제 시
balance 변화 없음
locked += amount

정산 시
payer.balance -= amount
payer.locked -= amount
receiver.balance += amount

---

## 9. Journey 조회

```
qr_token
  → product_item
  → production_batch
  → production_input
  → material
       │
       ├─ material_trade        거래 이력 (ID 변화 없음)
       │
       └─ material_lineage      혈통 (재귀)
              ↓
          parent material
              ├─ relation_type = PROCESSING        → processing_job (업체·공정·Loss)
              └─ relation_type = PRODUCTION_SCRAP  → production_batch
```

거래는 Material ID 를 바꾸지 않으므로 `material_trade` 에서 별도 조회한다.

**재귀 깊이 제한이 필요하다.** 자투리가 다시 제품이 되고 그 제품이 회수되면
2·3세대가 생겨 순환 참조 위험이 있다.

## **반려 이력 처리**

반려된 Processing Job은 최종 Material Journey에
성공한 Processing 단계로 연결하지 않는다.

즉 REJECTED Processing에서는 Child Material 및
material_lineage를 생성하지 않는다.

단, 상세 검증 이력에서는 다음 Blockchain Transaction을 조회할 수 있다.

- Processor 결과 제출
- Brand 결과 반려

따라서 최종 소재 혈통과 감사용 Blockchain 이력을 분리해서 제공한다.

MySQL 재귀 CTE 예시:

sql

```sql
WITH RECURSIVE lineage AS (    SELECT child_material_id, parent_material_id, relation_type, reference_id, 1 AS depth    FROM material_lineage WHERE child_material_id = ?  UNION ALL    SELECT ml.child_material_id, ml.parent_material_id, ml.relation_type, ml.reference_id, l.depth + 1    FROM material_lineage ml    JOIN lineage l ON ml.child_material_id = l.parent_material_id    WHERE l.depth < 10)SELECT * FROM lineage;
```

10. 인덱스 정리
테이블	인덱스
users	UNIQUE email
user_wallet	UNIQUE wallet_address, user_id
material	(material_type, root_origin), verification_state
material_balance	UNIQUE (material_id, owner_id), owner_id
material_lock	(material_id, owner_id, status), (reference_type, reference_id), expires_at
material_lineage	child_material_id, parent_material_id
evidence_file	(reference_type, reference_id)
processing_request	(brand_id, status), expires_at
processing_quote	(processing_request_id, status), processor_id, UNIQUE (processing_request_id, processor_id)
processing_job	(processor_id, status), (brand_id, status)
processing_input	processing_job_id, material_id
processing_output	processing_job_id, output_material_id
processor_capability	UNIQUE (processor_profile_id, material_type, process_type)
production_batch	(producer_id, status), product_model_id
production_input	UNIQUE (production_batch_id, material_id), material_id
product_item	(product_model_id, status), UNIQUE qr_token
product_order	(buyer_id, status), (brand_id, status)
product_order_item	UNIQUE product_item_id
blockchain_transaction	(reference_type, reference_id), tx_hash
