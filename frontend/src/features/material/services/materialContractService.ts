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
import { env } from '../../../config/env.ts'
import { getReadContract, getWriteContract } from '../../../services/blockchain/contractFactory.ts'
import { requireOnChainRole } from '../../../services/blockchain/onChainRole.ts'
import type { ChainLinkPayload } from '../../../services/blockchain/chainLinkApi.ts'
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
  // mintMaterial 은 onlyRole(SUPPLIER_ROLE) 이다 (MaterialToken.sol:257).
  // 권한이 없으면 발행이 통째로 되돌려지므로, 서명 팝업을 띄우기 전에 알려 준다.
  await requireOnChainRole('SUPPLIER_ROLE')
  return contract.mintMaterial(
    to,
    amount,
    toOnChainRootOrigin(rootOrigin),
    metadataUri,
    evidenceHash,
  ) as Promise<ContractTransactionResponse>
}

/**
 * 이 소재가 <b>이미 발행돼 있는지</b> 체인에서 찾는다.
 *
 * <b>왜 필요한가</b> — 발행은 두 번의 쓰기다. 지갑 서명(mint)과 그 결과를 서버에 알리는
 * 요청(link). 서명은 끝났는데 link 가 실패하면 체인에는 토큰이 있고 DB 는 모르는 상태가
 * 된다. {@code useOnChainLink} 가 그 영수증을 ref 에 붙들어 두지만 <b>ref 는 메모리라
 * 새로고침하면 사라진다.</b> 그러면 다음 시도가 mint 부터 다시 보내 토큰이 하나 더 생기고,
 * 되돌릴 수 없다. 2026-09-08 에 tokenId 2 가 그렇게 고아로 남았다.
 *
 * 그래서 서명 전에 체인에 묻는다. 체인이 정본이므로 여기서 찾으면 mint 를 보내지 않고
 * 서버 기록만 이어서 하면 된다.
 *
 * <b>{@code metadataUri} 가 대조 키다.</b> 발행 시 `saeroi://material/MAT-000014` 형태로
 * material_code 를 실어 보내므로(MaterialForm), 그 문자열이 그대로 소재를 가리킨다.
 * 이벤트에서는 인덱싱되지 않은 값이라 필터로 좁힐 수 없어, 인덱싱된 {@code operator}
 * (= 서명한 지갑)로 먼저 줄이고 나머지는 코드에서 비교한다.
 *
 * 못 찾으면 {@code null}. <b>조회가 실패해도 던지지 않고 {@code null} 을 준다</b> —
 * RPC 가 흔들릴 때 발행 자체를 막으면 정상 사용자가 아무것도 못 하게 된다. 그때는
 * 이 가드가 없던 예전과 같은 상태로 돌아갈 뿐이다.
 */
export async function findMintedMaterial(
  registrar: string,
  metadataUri: string,
): Promise<ChainLinkPayload | null> {
  try {
    const contract = getReadContract('materialToken')
    // **제네시스부터 훑지 않는다.** 컨트랙트가 배포된 블록부터 본다 — 그 앞에는 이
    // 컨트랙트의 로그가 존재할 수 없다. 0 부터 조회하면 SSAFY 체인에서 33초가 걸리고
    // (2026-09-11 실측, 블록 1,122만) 그 시간이 통째로 **서명 팝업 앞을 막는다.**
    // 같은 질의가 배포 블록부터면 455ms 다.
    const events = await contract.queryFilter(
      contract.filters.MaterialMinted(registrar, null, null),
      env.chainFromBlock,
      'latest',
    )

    // 같은 소재를 두 번 발행했다면(이 가드가 없던 시절에 생긴 고아) 가장 오래된 것을
    // 정본으로 삼는다. 뒤의 것을 고르면 앞의 토큰이 영구히 가리켜지지 않는다.
    for (const event of events) {
      if (!('args' in event) || event.args === undefined) continue
      if (event.args.metadataUri !== metadataUri) continue
      return {
        onChainId: Number(event.args.tokenId as bigint),
        txHash: event.transactionHash,
        blockNumber: event.blockNumber,
      }
    }
    return null
  } catch {
    return null
  }
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
 *
 * 📌 가공 검증(`verifyProcessing`)은 **MaterialToken 에 없다.**
 * 처음에는 있다고 보고 서비스에 넣었는데, blockchain 저장소의 실제 artifact 를 대조해 보니
 * 그 기능은 `ProcessingRegistry` 로 분리되어 있었다. 가공 도메인(4번 담당)이
 * 그쪽 컨트랙트로 구현한다.
 */
export async function verifyEvidence(tokenId: number, hash: string): Promise<boolean> {
  const contract = getReadContract('materialToken')
  return contract.verifyEvidence(tokenId, hash) as Promise<boolean>
}

/**
 * 출처 검증(`verifySource`). 최초 등록 SUPPLIER 가 "이 출처가 사실이다" 라고
 * 서명하는 독립 행위다 — mint 와 달리 새로 채번되는 값이 없다
 * (`docs/superpowers/specs/2026-09-07-material-verification-state.md`).
 */
export async function verifySource(
  tokenId: number,
  evidenceHash: string,
): Promise<ContractTransactionResponse> {
  const contract = await getWriteContract('materialToken')
  // mintMaterial 과 같은 이유로 미리 확인한다 — revert 를 서명 팝업 뒤에서 보게 하지 않는다.
  await requireOnChainRole('SUPPLIER_ROLE')
  return contract.verifySource(tokenId, evidenceHash) as Promise<ContractTransactionResponse>
}
