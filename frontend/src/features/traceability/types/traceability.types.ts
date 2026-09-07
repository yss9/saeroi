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

/** QR Passport 공개 조회 응답. 로그인 없이 접근 가능한 데이터만 담는다. */
export interface ProductPassport {
  productId: number
  productName: string
  brandName: string
  producedAt: string
  steps: JourneyStep[]
}

// TODO(6번/Traceability 담당): 온체인 검증 결과 타입을 추가한다.
