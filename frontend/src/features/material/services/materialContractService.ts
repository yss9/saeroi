// Material 관련 온체인 호출.
//
// Component 는 이 파일의 함수만 부른다. `new ethers.Contract(...)` 는 여기에도 없다 —
// contractFactory 가 주소·ABI·runner 조립을 담당한다.
//
// 호출 경로: Page → Feature Hook → 여기 → contractFactory → walletService → ethers → MetaMask
//
// 수량 인자는 전부 **gram 정수** 다 (정책 03절). kg 은 utils/quantity.ts 에서 변환한다.

import type { ContractTransactionResponse } from 'ethers'
import {
  BURN_REASON_ON_CHAIN,
  ROOT_ORIGIN_ON_CHAIN,
  type BurnReasonOnChain,
  type RootOriginOnChain,
} from '../../../config/abi/onchainEnums.ts'
import { getReadContract, getWriteContract } from '../../../services/blockchain/contractFactory.ts'
import type { MaterialRootOrigin } from '../types/material.types.ts'

/** 도메인 문자열 → 컨트랙트가 받는 uint8. */
export function toOnChainRootOrigin(origin: MaterialRootOrigin): RootOriginOnChain {
  return ROOT_ORIGIN_ON_CHAIN[origin]
}

export interface MintMaterialParams {
  /** 발행 대상 지갑. 보통 등록자 본인이다. */
  to: string
  /** gram 정수 */
  amount: number
  rootOrigin: MaterialRootOrigin
  /** 소재 Metadata JSON 경로 (Object Storage) */
  metadataUri: string
  /** Evidence Manifest SHA-256, `0x` 접두 66자 (정책 13절) */
  evidenceHash: string
}

/**
 * Material Token 발행.
 *
 * ⚠️ `tokenId` 는 **인자가 아니라 반환값** 이다. 어떤 소재인지 미리 정해 보내는 게 아니라
 * 컨트랙트가 채번한다. 그리고 트랜잭션으로 부를 때는 그 반환값을 읽을 수 없으므로,
 * 확정 후 `MaterialMinted` 이벤트에서 꺼내야 한다 (contractFactory.readEventArg).
 *
 * 여기서는 전송만 하고 receipt 는 기다리지 않는다 —
 * 대기·상태 전이는 useBlockchainTransaction 이 공통으로 처리한다.
 */
export async function mintMaterial({
  to,
  amount,
  rootOrigin,
  metadataUri,
  evidenceHash,
}: MintMaterialParams): Promise<ContractTransactionResponse> {
  const contract = await getWriteContract('materialToken')
  return contract.mintMaterial(
    to,
    amount,
    toOnChainRootOrigin(rootOrigin),
    metadataUri,
    evidenceHash,
  ) as Promise<ContractTransactionResponse>
}

/**
 * 소유권 이전 (ERC-1155).
 *
 * 인자가 5개다. `from` 이 필요한 이유는 ERC-1155 가 대리 전송(operator)을 지원하기 때문이다.
 * 본인이 보낼 때도 자기 주소를 넣어야 한다.
 *
 * 📌 Marketplace 거래에서는 이 함수를 직접 부르지 않는다. Buyer 수령 확인
 * (`confirmReceipt`) 시점에 Marketplace 컨트랙트가 이전을 수행한다 (정책 07.2).
 * 이 함수는 마켓 밖 직접 이전용이다.
 */
export async function transferMaterial(
  from: string,
  to: string,
  tokenId: number,
  amount: number,
): Promise<ContractTransactionResponse> {
  const contract = await getWriteContract('materialToken')
  return contract.safeTransferFrom(from, to, tokenId, amount, '0x') as Promise<ContractTransactionResponse>
}

/**
 * Token 소각.
 *
 * 소각 사유를 반드시 함께 남긴다 — 가공 Loss 인지, 생산 폐기인지, 제품에 사용되어
 * Redeem 된 것인지는 Journey 에서 서로 다른 의미를 갖는다 (정책 12·14절).
 */
export async function burnMaterial(
  from: string,
  tokenId: number,
  amount: number,
  reason: BurnReasonOnChain = BURN_REASON_ON_CHAIN.PROCESSING_LOSS,
): Promise<ContractTransactionResponse> {
  const contract = await getWriteContract('materialToken')
  return contract.burnMaterial(from, tokenId, amount, reason) as Promise<ContractTransactionResponse>
}

// ── 조회 (서명 불필요) ──────────────────────────────────

export interface OnChainBalance {
  /** 총 보유량 (gram) */
  total: bigint
  /** 잠긴 수량 (gram) */
  locked: bigint
  /** 사용 가능 수량 = total − locked (gram) */
  available: bigint
}

/**
 * 보유량을 한 번에 조회한다.
 *
 * 세 값을 따로 부르면 호출 사이에 상태가 바뀌어 `available !== total − locked` 인
 * 화면이 나올 수 있다. 같은 시점에 묶어서 읽는다.
 *
 * 📌 정책 04절: 이 값이 **Source of Truth** 다. 서버 응답과 다르면 이쪽이 맞다.
 */
export async function getOnChainBalance(
  owner: string,
  tokenId: number,
): Promise<OnChainBalance> {
  const contract = getReadContract('materialToken')
  const [total, locked, available] = await Promise.all([
    contract.balanceOf(owner, tokenId) as Promise<bigint>,
    contract.lockedBalanceOf(owner, tokenId) as Promise<bigint>,
    contract.availableBalance(owner, tokenId) as Promise<bigint>,
  ])
  return { total, locked, available }
}

/**
 * 증빙 해시가 온체인 기록과 일치하는지 확인한다.
 * QR Passport 의 "증빙 무결성 확인" 이 이 함수를 쓴다.
 */
export async function verifyEvidence(tokenId: number, hash: string): Promise<boolean> {
  const contract = getReadContract('materialToken')
  return contract.verifyEvidence(tokenId, hash) as Promise<boolean>
}
