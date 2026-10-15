// 확정된 온체인 트랜잭션 결과를 서버에 알린다.
//
// 이 계층의 규칙:
//   - apiClient 만 사용한다 (axios 를 직접 import 하지 않는다)
//   - React 를 모른다 (훅·컴포넌트 import 금지)
//
// ⚠️ **체인 ID 와 컨트랙트 주소를 보내지 않는다.** 서버가 설정으로 들고 있다.
// 화면이 보내면 자기가 배포한 컨트랙트를 가리키는 "온체인에 등록됨" 기록을 만들 수 있고,
// 서버가 체인에서 다시 확인하지 않는 한 구분되지 않는다.
//
// ⚠️ 경로가 섞여 있다 — 소재는 무버전(`/materials`), 판매글·거래는 `/v1`.
// 실수가 아니라 각 도메인의 기존 관례를 따른 것이다. 통일은 이메일 로그인 컨트롤러가
// 들어올 때 프론트·백엔드를 같은 주기에 함께 옮긴다.

import { apiClient } from '../apiClient.ts'

export interface ChainLinkPayload {
  /** 체인이 채번한 번호. 이벤트에서 꺼낸 값이다 — 트랜잭션의 반환값은 영수증에 없다. */
  onChainId: number
  /** `0x` + 64 hex. 형식이 어긋나면 서버가 400 으로 거절한다. */
  txHash: string
  blockNumber?: number
}

/** 소재 발행(`mintMaterial`) 결과. 등록자 본인만 부를 수 있다. */
export async function linkMaterialOnChain(
  materialId: number,
  payload: ChainLinkPayload,
): Promise<void> {
  await apiClient.post(`/materials/${materialId}/chain`, payload)
}

/** 판매 등록(`createListing`) 결과. 판매자 본인만 부를 수 있다. */
export async function linkListingOnChain(
  listingId: number,
  payload: ChainLinkPayload,
): Promise<void> {
  await apiClient.post(`/v1/listings/${listingId}/chain`, payload)
}

/** 주문 생성(`createOrder`) 결과. 구매자 본인만 부를 수 있다. */
export async function linkTradeOnChain(
  tradeId: number,
  payload: ChainLinkPayload,
): Promise<void> {
  await apiClient.post(`/v1/trades/${tradeId}/chain`, payload)
}

/**
 * 출처 검증(`verifySource`) 확정 결과.
 *
 * `onChainId` 가 없다 — mint 와 달리 새로 채번되는 값이 없어서다. 등록자 본인만
 * 부를 수 있고, 서버는 이미 검증된 소재를 다시 확정하면 거절한다
 * (`MaterialSourceVerificationApiTests`).
 */
export interface SourceVerificationPayload {
  txHash: string
  blockNumber?: number
}

export async function confirmSourceVerification(
  materialId: number,
  payload: SourceVerificationPayload,
): Promise<void> {
  await apiClient.post(`/materials/${materialId}/source-verification`, payload)
}
