// Traceability / QR Passport 도메인 타입.
// Backend `material_lineage`, `blockchain_transaction`, `evidence_file` 에 대응한다.

/** `material_lineage.relation_type` — 소재가 다음 소재로 이어진 경위 */
export type LineageRelationType = 'PROCESSING' | 'PRODUCTION_SCRAP'

/** `blockchain_transaction.status` — 온체인/오프체인 불일치 구간을 표현한다 */
export type BlockchainTxStatus = 'PENDING' | 'SUCCESS' | 'FAILED'

/** `blockchain_transaction.reference_type` */
export type TxReferenceType = 'MATERIAL' | 'TRADE' | 'PROCESSING' | 'PRODUCTION' | 'DISPUTE'

/** `evidence_file.evidence_type` */
export type EvidenceType =
  | 'BEFORE_PHOTO'
  | 'AFTER_PHOTO'
  | 'WEIGHT_SLIP'
  | 'INVOICE'
  | 'PROCESS_PHOTO'
  | 'ETC'

/** Material Journey 타임라인의 한 단계. */
export interface JourneyStep {
  /** 단계 종류 — 소재 등록 / 거래 / 가공 / 생산 */
  referenceType: TxReferenceType
  title: string
  occurredAt: string
  /** 온체인 트랜잭션 해시. 아직 기록 전이면 null */
  txHash: string | null
  txStatus: BlockchainTxStatus | null
  /** 증빙 원본 해시. 무결성 검증에 쓴다 */
  evidenceHash: string | null
}

/** `product_item.status` — Passport 가 확인해 주는 그 개체 하나의 상태 */
export type PassportItemStatus = 'IN_STOCK' | 'RESERVED' | 'SOLD'

/**
 * Passport 가 공개하는 제품 정보. `features/product` 의 `ProductModel` 을 그대로 쓰지
 * 않는다 — Backend `passport/dto/PassportProductSummary.java` 가 의도적으로 좁힌
 * 응답이고, 여기서 다시 넓히면(`ProductModel` 을 import 해서 캐스팅하는 식으로) 그 좁힘이
 * 프론트 타입에서 도로 사라진다.
 *
 * **`thumbnailUrl` 이 없다.** 저장 키 문자열이 브랜드 내부 id 를 경로에 담고 있어
 * Backend 가 응답에서 뺐다 (§6-3 결정 보강, 2026-09-09). 이미지는 `getPassportThumbnailUrl`
 * 로 곧장 받는다.
 */
export interface PassportProduct {
  name: string
  category: 'BAG' | 'WALLET' | 'CLOTHING' | 'ACCESSORY' | 'HOME' | 'ETC'
  description: string
}

/**
 * QR Passport 공개 조회 응답 (`GET /v1/qr/{qrToken}`). 로그인 없이 접근 가능한
 * 데이터만 담는다. Backend 원본: `passport/dto/PassportResponse.java`.
 *
 * **`productId`·`brandName` 이 없다.** 이전 초안(이 타입의 옛 버전)은 이 둘을 가정하고
 * 있었는데, 실제 §6-3(2026-09-08) 결정은 순번 id 와 거래 정보(브랜드 식별자 포함)를
 * 공개 응답에서 뺐다 — `qrToken` 을 우회해 다른 제품을 순서대로 훑어볼 실마리를 주지
 * 않기 위해서다.
 *
 * **`steps: JourneyStep[]` 도 없다.** Backend 의 Production 도메인(생산 배치·투입·산출)이
 * 아직 없어 이 개체를 소재 혈통까지 이을 방법이 없다 — `traceabilityAvailable` 이
 * 그 사실을 그대로 알려준다. `false` 인 동안 화면은 "제품 확인" 까지만 보여주고
 * `JourneyStep` 타임라인은 그리지 않는다.
 */
export interface ProductPassport {
  itemCode: string
  serialNumber: number
  itemStatus: PassportItemStatus
  issuedAt: string
  product: PassportProduct
  traceabilityAvailable: boolean
}

// TODO(6번/Traceability 담당): Production 도메인이 생기면 steps: JourneyStep[] 를 추가한다.
