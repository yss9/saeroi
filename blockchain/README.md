# Circular Fashion Blockchain

Solidity + Hardhat 기반 스마트 컨트랙트 프로젝트입니다.

현재 단계에서는 React, MetaMask, Spring, Web3j 연동을 제외하고 `MaterialToken` ERC-1155 PoC부터 검증합니다.

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
│   └── MaterialToken.sol
├── scripts/
│   └── deploy.ts
├── test/
│   └── MaterialToken.ts
├── deployments/
├── abi/
├── hardhat.config.ts
├── package.json
└── .env.example
```

## MaterialToken PoC

`MaterialToken`은 OpenZeppelin ERC-1155와 AccessControl을 사용합니다.

초기 검증 범위는 다음과 같습니다.

- Supplier가 Material Token을 발행합니다.
- 하나의 `tokenId`는 하나의 Material Batch를 의미합니다.
- 수량은 정수 단위로 관리합니다. 예: 50kg = 50000g.
- Supplier가 Brand에게 일부 수량을 전송할 수 있습니다.
- 같은 `tokenId`의 metadata URI는 최초 발행 후 변경하지 않습니다.

## Roles

```text
DEFAULT_ADMIN_ROLE
SUPPLIER_ROLE
PROCESSOR_ROLE
BRAND_ROLE
VERIFIER_ROLE
```

현재 PoC에서는 `SUPPLIER_ROLE`만 `mintMaterial` 권한에 직접 사용합니다.
다른 역할은 Processing, Production, Verification 확장 단계에서 사용할 예정입니다.

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
