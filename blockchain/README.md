# Circular Fashion Blockchain

Solidity + Hardhat 기반 스마트 컨트랙트 프로젝트입니다.

현재 단계에서는 React, MetaMask, Spring, Web3j 연동을 제외하고 `MaterialToken` ERC-1155, `PaymentToken` ERC-20, `Marketplace` 소재 잠금 거래 흐름을 검증합니다.

## Branch

이 브랜치는 Git Convention의 Blockchain 전략을 따릅니다.

```text
blockchain/develop
└── blockchain/feat/material-token
```

## Structure

```text
.
├── contracts/
│   ├── MaterialToken.sol
│   ├── PaymentToken.sol
│   └── Marketplace.sol
├── scripts/
│   ├── deploy.ts
│   └── export-abi.ts
├── test/
│   ├── MaterialToken.ts
│   ├── PaymentToken.ts
│   └── Marketplace.ts
├── deployments/
├── abi/
├── hardhat.config.ts
├── package.json
└── .env.example
```

## MaterialToken

`MaterialToken`은 OpenZeppelin ERC-1155와 AccessControl을 사용합니다.

검증 범위는 다음과 같습니다.

- Supplier가 Material Token을 발행합니다.
- 하나의 `tokenId`는 하나의 Material Batch를 의미합니다.
- 수량은 정수 단위로 관리합니다. 예: 50kg = 50000g.
- Supplier가 Brand에게 일부 수량을 전송할 수 있습니다.
- 같은 `tokenId`의 metadata URI는 최초 발행 후 변경하지 않습니다.
- Marketplace가 `(owner, tokenId, quantity)` 기준으로 일부 수량만 잠글 수 있습니다.
- 잠긴 수량은 전송과 소각에 사용할 수 없습니다.

## Marketplace

`Marketplace`는 판매글 전체 내용을 온체인에 저장하지 않고 거래에 필요한 핵심 제약만 보장합니다.

- 판매 등록 시 판매자의 소재 수량을 잠급니다.
- 부분 구매를 위해 판매글의 최초 수량과 잔여 수량을 분리합니다.
- 구매 주문 시 `PaymentToken`을 컨트랙트에 예치합니다.
- 구매자 수령 확인 시 소재 이전, 결제 정산, 잠금 해제를 하나의 트랜잭션으로 처리합니다.
- 검색, 제목, 설명, 사진, 배송 정보는 오프체인 DB에서 관리하는 것을 전제로 합니다.

## Roles

```text
DEFAULT_ADMIN_ROLE
SUPPLIER_ROLE
PROCESSOR_ROLE
BRAND_ROLE
STATUS_MANAGER_ROLE
MATERIAL_LOCK_MANAGER_ROLE
```

현재 PoC에서는 `SUPPLIER_ROLE`만 `mintMaterial` 권한에 직접 사용합니다.
별도 검증자 역할은 사용하지 않으며, 발행된 Material은 `REGISTERED` 상태에서 바로 거래 등록할 수 있습니다.
`PROCESSOR_ROLE`, `BRAND_ROLE`은 Processing과 Production 확장 단계에서 사용할 예정입니다.
`MATERIAL_LOCK_MANAGER_ROLE`은 Marketplace에 부여해 소재 잠금과 해제를 처리합니다.

## Commands

```bash
npm install
npm run compile
npm test
npm run abi
```

## RPC Checks

RPC는 컨트랙트를 배포하는 기능이 아니라, 블록체인 노드와 통신하는 입구입니다.
아래 명령은 네트워크 정보, 최신 블록 번호, 계정 잔액, 주소의 컨트랙트 여부만 조회합니다.

```bash
npm run rpc:local
npm run rpc:ssafy
```

EOA에서 EOA로 기본 송금 트랜잭션을 보내려면 `.env`에 `TRANSFER_TO_ADDRESS`를 넣은 뒤 실행합니다.
이 명령은 컨트랙트를 배포하지 않지만, 실제 네트워크 상태를 바꾸는 트랜잭션입니다.

```bash
npm run tx:ssafy
```

## SSAFY Besu Draft

`.env.example`을 `.env`로 복사한 뒤 SSAFY에서 받은 RPC 정보를 입력합니다.

```dotenv
SSAFY_RPC_URL=https://<SSAFY_BESU_RPC_URL>
SSAFY_CHAIN_ID=<SSAFY_CHAIN_ID>
DEPLOYER_PRIVATE_KEY=<DEPLOYER_PRIVATE_KEY_WITHOUT_0x>
TARGET_ADDRESS=<ADDRESS_TO_READ_BALANCE_OR_CODE>
TRANSFER_TO_ADDRESS=<RECEIVER_ADDRESS_FOR_EOA_TRANSFER>
TRANSFER_AMOUNT_ETH=0.001
```

배포는 로컬 테스트 통과 후 수동으로 실행합니다.

```bash
npm run deploy:ssafy
```
