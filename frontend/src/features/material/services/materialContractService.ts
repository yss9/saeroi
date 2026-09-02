// Material 관련 온체인 호출.
//
// Component 는 이 파일의 함수만 부른다. `new ethers.Contract(...)` 는 여기에도 없다 —
// contractFactory 가 주소·ABI·signer 조립을 담당한다.
//
// 호출 경로: Page → useMintMaterial → 여기 → contractFactory → walletService → ethers → MetaMask
//
// ⚠️ ABI 가 아직 비어 있어 실제 호출은 실패한다(`isContractConfigured` 가 false).
// TODO(1번/Blockchain 담당): blockchain/master 의 MaterialToken ABI 가 나오면
// src/config/contracts.ts 의 abis.materialToken 에 연결한다. 그 순간 아래 함수가 동작한다.

import type { ContractTransactionResponse } from 'ethers'
import { getReadContract, getWriteContract } from '../../../services/blockchain/contractFactory.ts'

/**
 * Material Token 발행.
 * 트랜잭션을 보내기만 하고 receipt 는 기다리지 않는다 —
 * 대기·상태 전이는 useBlockchainTransaction 이 공통으로 처리한다.
 */
export async function mintMaterialToken(
  materialId: number,
  quantity: number,
): Promise<ContractTransactionResponse> {
  const contract = await getWriteContract('materialToken')
  return contract.mint(materialId, quantity) as Promise<ContractTransactionResponse>
}

/** 소유권 이전. 사용자가 MetaMask 에서 직접 서명한다. */
export async function transferMaterialToken(
  to: string,
  tokenId: number,
  quantity: number,
): Promise<ContractTransactionResponse> {
  const contract = await getWriteContract('materialToken')
  return contract.safeTransferFrom(to, tokenId, quantity) as Promise<ContractTransactionResponse>
}

/** 잔량 조회. 서명이 필요 없으므로 읽기 전용 컨트랙트를 쓴다. */
export async function getMaterialBalance(owner: string, tokenId: number): Promise<bigint> {
  const contract = await getReadContract('materialToken')
  return contract.balanceOf(owner, tokenId) as Promise<bigint>
}
