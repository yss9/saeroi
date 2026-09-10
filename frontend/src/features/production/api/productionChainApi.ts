// 생산 배치의 온체인 결과를 서버에 알린다.
//
// ⚠️ **체인 ID 와 컨트랙트 주소를 보내지 않는다.** 서버 설정이 정본이다 — 화면이 보내면
// 자기가 배포한 컨트랙트를 가리키는 "온체인에 등록됨" 기록을 만들 수 있다
// (`chainLinkApi.ts` 와 같은 규칙).

import { apiClient } from '../../../services/apiClient.ts'
import type { ProductionManifest } from '../types/production.types.ts'

export interface LinkChainBatchPayload {
  /** `ProductionBatchCreated` 이벤트의 `batchId` */
  chainBatchId: number
  /** `0x` + 64 hex */
  txHash: string
  blockNumber?: number
}

/**
 * `createBatch` 결과 — `POST /v1/production-batches/{batchId}/chain/batch`
 *
 * **확정된 배치만, 생산자 본인만, 한 번만** 가능하다. 두 번 부르면
 * `PRODUCTION_CHAIN_BATCH_ALREADY_LINKED` 다.
 *
 * 서버가 `chainBatchId` 를 보관하는 이유는 뒤의 세 단계가 모두 그 번호를 인자로 받기
 * 때문이다 — 화면이 들고 있으면 새로고침 한 번에 잃어버리고, 그러면 이미 체인에 만들어진
 * 배치를 다시 가리킬 수 없다.
 */
export async function linkChainBatch(
  batchId: number,
  payload: LinkChainBatchPayload,
): Promise<void> {
  await apiClient.post(`/v1/production-batches/${batchId}/chain/batch`, payload)
}

/**
 * 온체인에 올릴 manifest 와 그 해시 — `GET /v1/production-batches/{batchId}/chain/manifest`
 *
 * **화면이 해시를 계산하지 않는다.** 서버가 만든 값을 그대로 서명에 넣는다 — 계산 주체가
 * 둘이면 필드 순서·공백·인코딩 차이로 언젠가 갈라지고, 갈라진 사실은 대조할 때만 드러난다.
 * 서버도 보고받은 해시를 자기 계산과 대조한다.
 *
 * `submitPlanAndLock` 단계에서 쓴다. 지금은 그 단계가 배선돼 있지 않아 호출부가 없지만,
 * 계약이 확정돼 있어 함께 둔다.
 */
export async function getProductionManifest(batchId: number): Promise<ProductionManifest> {
  const { data } = await apiClient.get<ProductionManifest>(
    `/v1/production-batches/${batchId}/chain/manifest`,
  )
  return data
}
