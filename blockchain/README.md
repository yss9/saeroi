# Circular Fashion Blockchain

Solidity + Hardhat 기반 소재 이력·거래 스마트 컨트랙트 프로젝트입니다.

현재 구현 범위는 `MaterialToken` ERC-1155, `Marketplace`, `ProcessingRegistry`, `ProductionRegistry`입니다. 모든 결제·환불·정산은 백엔드/PG와 DB에서만 처리하며 블록체인에는 금액이나 결제 식별자를 기록하지 않습니다.

## Structure

```text
.
├── contracts/
│   ├── MaterialToken.sol
│   ├── Marketplace.sol
│   ├── ProcessingRegistry.sol
│   └── ProductionRegistry.sol
├── scripts/
│   ├── deploy.ts
│   └── export-abi.ts
├── test/
│   ├── MaterialToken.ts
│   └── Marketplace.ts
├── deployments/
├── abi/
├── hardhat.config.ts
├── package.json
└── .env.example
```

## MaterialToken

`MaterialToken`은 OpenZeppelin ERC-1155와 AccessControl을 사용합니다.

- 하나의 `tokenId`는 하나의 소재 배치를 의미하며 컨트랙트가 순서대로 발급합니다.
- DB의 `material.id`를 온체인 ID로 직접 사용하지 않습니다. 백엔드는 `MaterialMinted` 이벤트에서 `tokenId`를 읽어 DB에 매핑합니다.
- 수량은 gram 정수 단위입니다. 예: 50kg = 50000g.
- 최초 발행은 `SUPPLIER_ROLE`만 가능하며 `generationType`은 `INITIAL`로 기록됩니다.
- `rootOrigin`은 MVP 정책에 따라 `VIRGIN`, `WASTE` 중 하나입니다.
- 가공 결과에는 `A`, `B`, `C` 등급을 기록하며 최초 소재는 `NONE`입니다.
- 소재 자체 상태는 `ACTIVE`, `DEPLETED`만 관리합니다. 판매·배송 상태는 `Marketplace`에서 관리합니다.
- 증빙 manifest는 백엔드에서 SHA-256으로 계산한 뒤 `bytes32`로 저장합니다. DB에는 `0x`를 포함한 66자 hex 문자열로 저장합니다.
- 검증 단계는 상태값으로 중복 저장하지 않고 Supplier 서명, 거래 수령 서명, Processing 승인, Production 확정 기록에서 조회 시 파생합니다.
- 최초 등록 Supplier는 `verifySource`, 소재를 인수한 Brand는 `verifyTransaction`을 직접 호출하며 Processing 검증은 Processor 제출과 Brand 승인이 모두 완료되어야 확정됩니다.
- 각 검증 단계에는 signer, SHA-256 증빙 해시, 거래/가공 참조 ID, 블록 시간이 별도 기록됩니다.
- 잠금은 합계만 저장하지 않고 각 업무별 `lockId`, 소유자, 소재, 수량, 용도, 참조 ID, 생성·해제·만료 시각과 상태를 기록합니다.
- 잠금 용도는 `MARKETPLACE`, `PROCESSING`, `PRODUCTION`입니다.
- 잠긴 수량은 직접 전송하거나 소각할 수 없고, 잠금을 만든 작업 컨트랙트만 해제하거나 소비할 수 있습니다.

## Marketplace

`Marketplace`는 판매글과 거래에 필요한 핵심 상태 및 소재 잠금을 온체인에서 보장합니다. 제목, 설명, 사진, 배송지와 카카오페이 상세 데이터는 DB에서 관리합니다.

- `SUPPLIER_ROLE`을 가진 공급자만 판매를 등록할 수 있으며, 등록 시 판매글 전용 소재 잠금을 생성합니다. 브랜드는 구매만 가능합니다.
- 판매글은 `ACTIVE`, `SOLD_OUT`, `CANCELLED` 상태를 가집니다.
- 거래는 `CREATED → SHIPPED → RECEIVED → COMPLETED` 순서로 진행합니다.
- 주문 생성 시 판매 잔량만 예약합니다. 가격, 총액, PG 거래번호, 결제 성공 여부는 컨트랙트의 상태·이벤트·함수에 존재하지 않습니다.
- 백엔드가 DB의 오프체인 결제 완료를 확인한 뒤 판매자가 `markShipped`를 호출합니다.
- 판매자가 `markShipped`, 구매자가 `confirmReceipt`를 호출합니다.
- 수령 확인 시 판매글 잠금에서 주문 수량을 소비하면서 소재 소유권을 구매자에게 이전합니다.
- 발송 전 `CREATED` 주문은 구매자 또는 판매자가 취소할 수 있습니다.
- 배송 이후 분쟁은 관리자 판정 없이 판매자와 구매자가 같은 해결안에 서명하면 소재 잠금을 이전하거나 해제합니다.

## Processing / Production

- 가공 Job은 공정 유형을 하나 이상 기록하고 `CREATED → SHIPPED → RECEIVED → PROCESSING → RESULT_SUBMITTED → CONFIRMED`로 진행합니다.
- 출력별로 하나의 입력 소재를 지정하므로 MVP에서 N→1 혼합은 허용하지 않고 1→N 분할만 허용합니다.
- Brand 승인 시 입력 무게 토큰을 소각하고 가공 등급과 원천 계보를 유지한 자식 무게 토큰을 발행합니다.
- WASTE 원재료(`INITIAL`)는 생산에 직접 투입할 수 없고, 반드시 가공된 소재(`PROCESSED`)여야 합니다.
- 생산 확정 시 `productUsed + scrap + waste = inputAmount`를 강제하고, WASTE 원천 제품 투입량 비율을 basis point로 저장합니다.

## Shop / Product Passport

- Shop 판매 상품의 이름, 이미지, 가격, 재고, 주문, 배송, 결제와 추측 불가능한 `qrToken`은 DB/Object Storage에서 관리합니다.
- 블록체인은 확정된 `ProductionBatch`와 생성된 `ProductItem ID`의 연결만 저장하고 `ProductLinked` 이벤트를 기록합니다.
- `getProductPassportProof(productItemId)`는 Batch ID, Brand, 업사이클 비율 스냅샷, Evidence Hash, 확정 시각을 공개 조회합니다. `view` 호출이므로 Consumer Wallet이나 서명이 필요하지 않습니다.
- Passport의 소재 구성은 반환된 Batch ID로 `getMaterialUses(batchId)`를 조회해 확인합니다.
- Shop 주문·결제·배송에서는 Material Token을 다시 차감하거나 Product 소유권 토큰을 전송하지 않습니다.

## Roles

```text
MaterialToken
├── DEFAULT_ADMIN_ROLE
├── SUPPLIER_ROLE
├── PROCESSOR_ROLE
├── BRAND_ROLE
├── MATERIAL_LOCK_MANAGER_ROLE
└── MATERIAL_WORKFLOW_ROLE

Marketplace
└── DEFAULT_ADMIN_ROLE

ProcessingRegistry / ProductionRegistry
├── DEFAULT_ADMIN_ROLE
├── BRAND_ROLE
└── PROCESSOR_ROLE (ProcessingRegistry only)
```

별도 결제 관리자·검증자 역할은 사용하지 않습니다.

## Deferred Scope

- RECOVERED 원천 유형
- 여러 입력을 하나의 출력으로 합치는 N→1 가공
- 물리적 검수·PG 결제·환불·정산의 온체인 처리

## Commands

```bash
npm install
npm run compile
npm test
npm run demo:flow
npm run abi
```

`npm run demo:flow`는 별도의 노드나 MetaMask 없이 Hardhat 메모리 네트워크와 가상 지갑을 사용합니다. 매 실행마다 컨트랙트를 새로 배포하고 Supplier 발행·원천 검증, Marketplace 잠금·주문·배송·수령, Brand 거래 검증까지 출력합니다. 결제는 오프체인 처리로만 표시되며 온체인 트랜잭션을 만들지 않습니다. 실행이 끝나면 메모리 네트워크의 데이터도 사라집니다.

## RPC Checks

아래 명령은 네트워크 정보, 최신 블록 번호, 계정 잔액, 주소의 컨트랙트 여부를 조회합니다.

```bash
npm run rpc:local
npm run rpc:ssafy
```

EOA 기본 송금 트랜잭션은 `.env`에 `TRANSFER_TO_ADDRESS`를 설정한 뒤 실행합니다.

```bash
npm run tx:ssafy
```

## SSAFY Besu Draft

`.env.example`을 `.env`로 복사하고 SSAFY RPC 정보를 입력합니다.

```dotenv
SSAFY_RPC_URL=https://<SSAFY_BESU_RPC_URL>
SSAFY_CHAIN_ID=<SSAFY_CHAIN_ID>
DEPLOYER_PRIVATE_KEY=<DEPLOYER_PRIVATE_KEY_WITHOUT_0x>
TARGET_ADDRESS=<ADDRESS_TO_READ_BALANCE_OR_CODE>
TRANSFER_TO_ADDRESS=<RECEIVER_ADDRESS_FOR_EOA_TRANSFER>
TRANSFER_AMOUNT_ETH=0.001
```

로컬 테스트 통과 후 아래 명령으로 네 컨트랙트를 배포합니다. 배포 스크립트는 Marketplace에 `MATERIAL_LOCK_MANAGER_ROLE`을, Processing/Production Registry에는 잠금 및 워크플로 역할을 부여합니다.

```bash
npm run deploy:ssafy
```
