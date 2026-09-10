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
