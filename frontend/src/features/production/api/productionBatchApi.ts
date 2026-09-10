// 생산 배치 API.
//
// 한 파일에 모은 이유 — 네 호출이 같은 리소스의 생애(열기 → 조회 → 확정)를 다루고 응답
// 타입이 하나(`ProductionBatch`)다. 파일을 넷으로 나누면 같은 import 를 네 번 쓰게 된다.
// (`paymentApi.ts` 가 같은 판단을 한다.)

import { apiClient } from '../../../services/apiClient.ts'
import type {
  OpenProductionBatchRequest,
  ProductionBatch,
  ProductionItem,
} from '../types/production.types.ts'

/**
 * 배치 열기 — `POST /v1/production-batches`
 *
 * `BRAND` 만 가능하고 **자기 제품에만** 열 수 있다 (`PRODUCTION_PRODUCT_MODEL_FORBIDDEN`).
 * 화면이 내 제품 목록에서 고르게 하면 이 거절은 나오지 않는다.
 *
 * ⚠️ **활성 지갑이 등록돼 있어야 한다** (`PRODUCTION_WALLET_NOT_LINKED`). 서명자 주소를
 * 서버가 등록된 지갑에서 가져와 배치에 굳히기 때문이다 — 바디로 받으면 남의 주소를
 * 서명자로 박아 넣을 수 있고, 그 주소가 온체인 기록에 그대로 남는다.
 *
 * 수량 세 컬럼과 업사이클 비율은 요청이 정하지 않는다. 확정 시점에 실제 투입으로 계산한다.
 */
export async function openProductionBatch(
  payload: OpenProductionBatchRequest,
): Promise<ProductionBatch> {
  const { data } = await apiClient.post<ProductionBatch>('/v1/production-batches', payload)
  return data
}

/** 배치 상세. 투입 목록을 함께 담는다. 남의 배치는 `PRODUCTION_FORBIDDEN` 이다. */
export async function getProductionBatch(batchId: number): Promise<ProductionBatch> {
  const { data } = await apiClient.get<ProductionBatch>(`/v1/production-batches/${batchId}`)
  return data
}

/**
 * 내 배치 목록 — `GET /v1/production-batches/me`
 *
 * 생산자 id 를 보내지 않는다. 서버가 JWT 에서 강제한다.
 */
export async function getMyProductionBatches(): Promise<ProductionBatch[]> {
  const { data } = await apiClient.get<ProductionBatch[]>('/v1/production-batches/me')
  return data
}

/**
 * 배치 확정 — `POST /v1/production-batches/{batchId}/confirm`
 *
 * **되돌릴 수 없다.** 이 한 번의 요청이 네 가지를 한다.
 *
 *   1) 투입한 소재를 소비한다 (잠금 소비 + 보유량 차감)
 *   2) 제품 개체를 생산 개수만큼 만든다 (각자 QR 토큰을 갖는다)
 *   3) 자투리를 새 소재로 발행하고 혈통을 잇는다
 *   4) 업사이클 비율을 계산해 배치에 굳힌다 (스냅샷이며 다시 계산하지 않는다)
 *
 * 바디가 없다. 확정에 필요한 것은 전부 이미 배치에 있고, 수량이나 비율을 여기서 받으면
 * 실제 투입과 다른 값을 적어 넣을 수 있다 — 그 값이 그대로 제품에 붙는다.
 *
 * 거절 조건: 투입이 하나도 없거나(`PRODUCTION_NO_INPUT`), 제품에 쓰인 수량이 전부 0 이거나
 * (`PRODUCTION_NO_PRODUCT_MATERIAL`), 이미 확정된 배치(`PRODUCTION_BATCH_NOT_EDITABLE`).
 *
 * ⚠️ **응답의 `inputs` 는 빈 배열이다** (`ProductionBatchResponse.of(batch, List.of())`).
 * 투입이 사라진 것이 아니다 — 확정 서비스가 목록을 다시 만들지 않는다. 이 응답을 상세
 * 캐시에 그대로 넣으면 화면에서 투입이 사라진다 (`useConfirmProductionBatch` 참고).
 */
export async function confirmProductionBatch(batchId: number): Promise<ProductionBatch> {
  const { data } = await apiClient.post<ProductionBatch>(
    `/v1/production-batches/${batchId}/confirm`,
  )
  return data
}

/**
 * 확정으로 만들어진 제품 개체 — `GET /v1/production-batches/{batchId}/items`
 *
 * **QR 라벨 인쇄를 위한 조회다.** 응답의 `qrToken` 이 비회원 공개 경로(`/qr/{token}`)의
 * 열쇠이므로 서버가 **배치 소유자에게만** 준다 — 남의 배치를 조회하면
 * `PRODUCTION_FORBIDDEN`(403) 이다.
 *
 * 확정 전에는 개체가 없어 빈 배열이다. 배치가 없거나 남의 것이면 에러이지, 빈 배열이
 * 아니다 — 그 둘을 화면이 구분할 수 있어야 한다.
 */
export async function getProductionItems(batchId: number): Promise<ProductionItem[]> {
  const { data } = await apiClient.get<ProductionItem[]>(
    `/v1/production-batches/${batchId}/items`,
  )
  return data
}
