# Circular Fashion Blockchain

Solidity + Hardhat 기반 소재 이력·거래 스마트 컨트랙트 프로젝트입니다.

현재 구현 범위는 `MaterialToken` ERC-1155와 `Marketplace`입니다. 결제는 카카오페이 가상 결제와 백엔드에서 처리하므로 ERC-20 `PaymentToken`은 사용하지 않습니다.

## Structure

```text
.
├── contracts/
│   ├── MaterialToken.sol
│   └── Marketplace.sol
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
- 소재 자체 상태는 `ACTIVE`, `DEPLETED`만 관리합니다. 판매·배송 상태는 `Marketplace`에서 관리합니다.
- 증빙 manifest는 백엔드에서 SHA-256으로 계산한 뒤 `bytes32`로 저장합니다. DB에는 `0x`를 포함한 66자 hex 문자열로 저장합니다.
- 검증 단계는 상태값으로 중복 저장하지 않고 Supplier 서명, 거래 수령 서명, Processing 승인, Production 확정 기록에서 조회 시 파생합니다.
- 최초 등록 Supplier는 `verifySource`, 소재를 인수한 Brand는 `verifyTransaction`을 직접 호출하며 Processing 검증은 Processor 제출과 Brand 승인이 모두 완료되어야 확정됩니다.
- 각 검증 단계에는 signer, SHA-256 증빙 해시, 거래/가공 참조 ID, 블록 시간이 별도 기록됩니다.
- 잠금은 합계만 저장하지 않고 각 업무별 `lockId`, 소유자, 소재, 수량, 용도, 참조 ID, 상태를 기록합니다.
- 잠금 용도는 `MARKETPLACE`, `PROCESSING`, `PRODUCTION`입니다.
- 잠긴 수량은 직접 전송하거나 소각할 수 없고, 잠금을 만든 작업 컨트랙트만 해제하거나 소비할 수 있습니다.

## Marketplace

`Marketplace`는 판매글과 거래에 필요한 핵심 상태 및 소재 잠금을 온체인에서 보장합니다. 제목, 설명, 사진, 배송지와 카카오페이 상세 데이터는 DB에서 관리합니다.

- 판매 등록 시 판매글 전용 소재 잠금을 생성합니다.
- 판매글은 `ACTIVE`, `SOLD_OUT`, `CANCELLED` 상태를 가집니다.
- 거래는 `CREATED → PAID → SHIPPED → RECEIVED → COMPLETED` 순서로 진행합니다.
- `DISPUTED` 값은 DB/ABI 정합성을 위해 예약되어 있으나 분쟁 처리 기능은 아직 구현하지 않았습니다.
- 주문 생성 시 판매 잔량만 예약하고 온체인 결제를 수행하지 않습니다.
- 카카오페이 결제가 성공하면 `PAYMENT_MANAGER_ROLE`을 가진 백엔드 지갑이 `markPaid`를 호출합니다.
- 판매자가 `markShipped`, 구매자가 `confirmReceipt`를 호출합니다.
- 수령 확인 시 판매글 잠금에서 주문 수량을 소비하면서 소재 소유권을 구매자에게 이전합니다.
- 결제 전 `CREATED` 주문은 구매자 또는 판매자가 취소할 수 있습니다.

### 가격 계산

판매 가격은 kg당 정수 가격이고 소재 수량은 gram입니다. 총액은 아래처럼 계산하며 나머지가 있으면 1원 올림합니다.

```text
totalPrice = (quantityGram * pricePerKg + 999) / 1000
```

## Roles

```text
MaterialToken
├── DEFAULT_ADMIN_ROLE
├── SUPPLIER_ROLE
├── PROCESSOR_ROLE
├── BRAND_ROLE
└── MATERIAL_LOCK_MANAGER_ROLE

Marketplace
├── DEFAULT_ADMIN_ROLE
└── PAYMENT_MANAGER_ROLE
```

`PROCESSOR_ROLE`, `BRAND_ROLE`은 Processing과 Production 확장 단계에서 사용할 예정입니다. 별도 검증자 역할은 사용하지 않습니다.

## Deferred Scope

- Processing 결과 다중 생성, 브랜드 승인, Material Lineage
- ProductionBatch Consume, 수량 보존, Scrap 발행, 중복 확정 방지
- 분쟁 접수 및 해결

## Commands

```bash
npm install
npm run compile
npm test
npm run demo:flow
npm run abi
```

`npm run demo:flow`는 별도의 노드나 MetaMask 없이 Hardhat 메모리 네트워크와 가상 지갑을 사용합니다. 매 실행마다 컨트랙트를 새로 배포하고 Supplier 발행·원천 검증, Marketplace 잠금·주문·결제 확인·배송·수령, Brand 거래 검증, Processor 가공 검증까지 한 번에 출력합니다. 실행이 끝나면 메모리 네트워크의 데이터도 사라집니다.

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

로컬 테스트 통과 후 아래 명령으로 `MaterialToken`과 `Marketplace`를 배포합니다. 배포 스크립트는 Marketplace에 `MATERIAL_LOCK_MANAGER_ROLE`도 부여합니다.

```bash
npm run deploy:ssafy
```
