// 생산 배치 API.
//
// 한 파일에 모은 이유 — 네 호출이 같은 리소스의 생애(열기 → 조회 → 확정)를 다루고 응답
// 타입이 하나(`ProductionBatch`)다. 파일을 넷으로 나누면 같은 import 를 네 번 쓰게 된다.
// (`paymentApi.ts` 가 같은 판단을 한다.)

import { apiClient } from '../../../services/apiClient.ts'
import type {
  AssignProductModelRequest,
  OpenProductionBatchRequest,
  ProductionBatch,
  ProductionItem,
} from '../types/production.types.ts'

/**
 * 배치 열기 — `POST /v1/production-batches`
 *
 * **무엇을 만들지는 여기서 정하지 않는다.** 실제 순서가 "배치 생성 → 소재 선택 → 사용량
 * 입력" 이라 소재도 고르기 전에 제품을 확정할 이유가 없다. 제품은 `assignProductModel`
 * 로 나중에 지정하고, 확정 전까지 채워지면 된다.
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

/**
 * 무엇을 만들지 정한다 — `PATCH /v1/production-batches/{batchId}`
 *
 * **`DRAFT` 일 때만 된다.** 서명이 시작된 뒤에는 거절한다 — 서명한 내용과 저장된 내용이
 * 달라지면 온체인 기록이 무엇을 증명하는지 알 수 없다.
 *
 * 이미 정해 둔 것을 바꾸는 데도 같은 경로를 쓴다. 남의 제품은
 * `PRODUCTION_PRODUCT_MODEL_FORBIDDEN` 이다.
 */
export async function assignProductModel(
  batchId: number,
  payload: AssignProductModelRequest,
): Promise<ProductionBatch> {
  const { data } = await apiClient.patch<ProductionBatch>(
    `/v1/production-batches/${batchId}`,
    payload,
  )
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
 * **제품은 없어도 된다.** 만들어진 개체는 제품 없이 남았다가 나중에 붙는다
 * (`assignProductModel` 이 개체까지 함께 채운다). 그때까지 그 개체는 어느 제품의 재고로도
 * 세지 않으므로 팔리지 않는다.
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
 * 배치 취소 — `POST /v1/production-batches/{batchId}/cancel`
 *
 * **묶인 소재를 풀어 주는 유일한 길이다.** 배치에 소재를 투입하면 그만큼 잠금이 걸리는데
 * 그 잠금에는 만료가 없다 — 기한이 지나 풀리면 확정 시점에 소재가 사라진 배치가 되기
 * 때문이다. 그래서 진행하지 않을 배치를 그냥 두면 그 소재를 다른 배치에도, 판매 글에도
 * 쓸 수 없다. 판매 등록이 같은 가용량 계산을 쓴다.
 *
 * 투입 행은 지워지지 않는다 — "이런 소재를 넣으려다 그만뒀다" 도 이력이다.
 *
 * 거절 조건: 이미 확정된 배치(`PRODUCTION_BATCH_NOT_EDITABLE`). 소재는 이미 소비됐고
 * 개체에는 번호가 붙었으므로, 그걸 되돌리는 일은 취소가 아니라 별도의 보정이다.
 */
export async function cancelProductionBatch(batchId: number): Promise<ProductionBatch> {
  const { data } = await apiClient.post<ProductionBatch>(
    `/v1/production-batches/${batchId}/cancel`,
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

/**
 * 개체의 QR 이미지 — `GET /v1/production-batches/{batchId}/items/{itemId}/qr`
 *
 * **라벨 인쇄가 쓰는 경로다.** PNG 바이트를 그대로 내려준다 (기본 512px).
 *
 * **`<img src>` 로는 못 쓴다** — 인증이 필요한데 브라우저는 `<img>` 요청에 Authorization
 * 헤더를 붙이지 않는다. 여기서 바이트를 받아 화면이 blob URL 로 바꿔 쓴다
 * (`useProductionItemQr`) — `getProductThumbnail` 과 같은 구조다.
 *
 * **개체를 id 로 지목한다.** 토큰으로 받으면 경로 자체가 공개 열쇠를 드러내고, 아무나 남의
 * QR 라벨을 만들어낼 수 있다. 서버도 배치 소유자에게만 준다 —
 * 남의 배치는 `PRODUCTION_FORBIDDEN`(403), 다른 배치의 개체는
 * `PRODUCTION_ITEM_NOT_FOUND`(404).
 *
 * **왜 서버가 그리는가** — 브랜드는 배치 하나에 라벨 수십 장을 한 번에 인쇄한다. 인쇄물은
 * 한 번 나가면 되돌릴 수 없으므로 모든 라벨이 같은 방식(같은 오류정정 수준·여백)으로
 * 만들어졌다는 보장이 중요하다. 프론트가 그리면 그 보장이 브라우저마다 갈라진다.
 *
 * 크기는 보내지 않는다. 서버 기본값 512px 이 라벨 인쇄에 맞춰진 값이고, 범위를 벗어난 값은
 * `PRODUCTION_QR_SIZE_INVALID`(400) 로 거절된다 — 화면이 고를 이유가 없다.
 */
export async function getProductionItemQr(
  batchId: number,
  productItemId: number,
): Promise<Blob> {
  const { data } = await apiClient.get<Blob>(
    `/v1/production-batches/${batchId}/items/${productItemId}/qr`,
    { responseType: 'blob' },
  )
  return data
}
