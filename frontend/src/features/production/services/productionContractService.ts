// ProductionRegistry 컨트랙트 호출.
//
// 이 계층의 규칙은 다른 도메인의 contract service 와 같다 — Provider·Signer·Contract 생성을
// 여기서 끝내고, 도메인 훅은 "무엇을 보낼지" 만 넘긴다. Component 에서
// `new ethers.Contract(...)` 를 쓰지 않는다.
//
// **온체인 4단계 중 이 파일이 다루는 것**
//
//   createBatch        온체인 배치 번호 채번            ← 지금 배선된 단계
//   submitPlanAndLock  투입 잠금 + 계획 해시 기록
//   confirmProduction  소재 소각 (레지스트리가 내부 호출)
//   linkProductItems   개체 연결
//
// 뒤의 세 단계는 아직 배선하지 않았다. `submitPlanAndLock` 은 인자 9개 중 셋
// (`tokenIds` · `scrapMetadataUris` · `scrapEvidenceHashes`)의 출처가 프론트에 없다 —
// 서버가 아는 값이므로 응답에 담아 주기를 요청해 둔 상태다.

import type { ContractTransactionResponse } from 'ethers'
import { getWriteContract } from '../../../services/blockchain/contractFactory.ts'

/**
 * 온체인 배치를 만든다. **4단계 중 첫 번째다.**
 *
 * 뒤의 세 단계가 모두 여기서 채번된 번호를 인자로 받으므로 이것 없이는 아무것도 할 수 없다.
 * 서버도 순서를 강제한다 — `createBatch` 결과를 알리지 않으면 다음 보고가
 * `PRODUCTION_CHAIN_BATCH_NOT_LINKED` 로 거절된다.
 *
 * **반환값(`batchId`)을 트랜잭션에서 받을 수 없다.** 선언이 `returns (uint256)` 이지만
 * 트랜잭션으로 부르면 영수증에 로그만 남는다. 번호는 `ProductionBatchCreated` 이벤트에서
 * 꺼낸다 (`mintMaterial` 의 `tokenId` 와 같은 사정이다).
 *
 * `BRAND_ROLE` 이 있는 지갑만 부를 수 있다 — 없으면 revert 한다.
 */
export async function createBatch(producedQuantity: number): Promise<ContractTransactionResponse> {
  const contract = await getWriteContract('productionRegistry')
  return contract.createBatch(producedQuantity) as Promise<ContractTransactionResponse>
}

/**
 * 투입을 잠그고 계획을 기록한다. **4단계 중 두 번째다.**
 *
 * 배열 인자 일곱 개가 **같은 순서로 짝을 이룬다** — 컨트랙트가 인덱스로 맞추므로, 서로 다른
 * 응답을 조합해 만들면 순서가 어긋나 엉뚱한 소재가 잠긴다. 그래서 서버의 manifest 한
 * 응답에서 통째로 만든다.
 *
 * 컨트랙트가 직접 거절하는 것들 — 수량이 보존되지 않음(`productUsed + scrap + waste !=
 * inputAmount`), 제품에 쓴 양이 0, 잔액 부족, 같은 소재 중복, **가공하지 않은 폐기물**
 * (`rootOrigin == WASTE && generation == INITIAL`), 자투리가 있는데 증빙 해시가 빔.
 *
 * 성공하면 투입마다 `MaterialLockCreated` 가 하나씩 나온다. 그 잠금 번호를 서버에 알려야
 * 3단계(소각)가 어느 잠금을 태울지 알 수 있다.
 */
export async function submitPlanAndLock(
  chainBatchId: number,
  evidenceHash: string,
  plan: {
    tokenIds: bigint[]
    inputAmounts: bigint[]
    productUsed: bigint[]
    scrap: bigint[]
    waste: bigint[]
    scrapMetadataUris: string[]
    scrapEvidenceHashes: string[]
  },
): Promise<ContractTransactionResponse> {
  const contract = await getWriteContract('productionRegistry')
  return contract.submitPlanAndLock(
    chainBatchId,
    evidenceHash,
    plan.tokenIds,
    plan.inputAmounts,
    plan.productUsed,
    plan.scrap,
    plan.waste,
    plan.scrapMetadataUris,
    plan.scrapEvidenceHashes,
  ) as Promise<ContractTransactionResponse>
}

/**
 * 생산을 확정한다. **4단계 중 세 번째이고, 이 서비스에서 가장 무거운 트랜잭션이다.**
 *
 * 잠긴 소재를 실제로 **소각**하고, 자투리가 있으면 **새 소재 토큰을 발행**한다 — 그 자투리는
 * 부모 소재를 가리킨 채로 다시 팔 수 있다. 순환이 실제로 도는 지점이다.
 *
 * 인자가 배치 번호 하나뿐인 이유 — 태울 대상과 수량은 2단계에서 이미 체인에 적혔다.
 * 여기서 다시 받으면 그때와 다른 값을 넣을 수 있고, 그건 계획과 결과가 어긋나는 길이다.
 */
export async function confirmProduction(
  chainBatchId: number,
): Promise<ContractTransactionResponse> {
  const contract = await getWriteContract('productionRegistry')
  return contract.confirmProduction(chainBatchId) as Promise<ContractTransactionResponse>
}

/**
 * 만들어진 개체를 배치에 연결한다. **4단계 중 마지막이다.**
 *
 * 이것이 끝나야 QR 을 찍었을 때 "이 제품 → 이 배치 → 이 소재들" 이 체인 위에서 이어진다.
 *
 * **개수가 1단계에서 선언한 수량과 정확히 같아야 한다** — 다르면 revert 한다. 한 번만 부를
 * 수 있고, 두 번째는 `products already linked` 로 거절된다.
 *
 * 온체인에서 개체 번호를 따로 채번하지 않는다. 우리 `product_item.id` 를 그대로 쓴다.
 */
export async function linkProductItems(
  chainBatchId: number,
  productItemIds: number[],
): Promise<ContractTransactionResponse> {
  const contract = await getWriteContract('productionRegistry')
  return contract.linkProductItems(chainBatchId, productItemIds) as Promise<ContractTransactionResponse>
}
