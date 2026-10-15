// Processing 도메인 타입. Backend `processing_*` 테이블 ENUM 과 일치한다.

/** `processing_job.process_type` / `processing_request_type.process_type` */
export type ProcessType =
  | 'CLEANING'
  | 'DISASSEMBLY'
  | 'SORTING'
  | 'CUTTING'
  | 'SHREDDING'
  | 'COLOR_PROCESSING'
  | 'SURFACE_FINISHING'
  | 'REMANUFACTURING'

/**
 * `processing_request.status`.
 *
 * 견적 단계가 없어졌다 — 브랜드가 검색 결과에서 가격과 소요일을 보고 바로 고르므로
 * `QUOTING`(견적 대기)·`SELECTED`(견적 수락)가 `REQUESTED`·`ACCEPTED` 로 바뀌었다.
 */
export type ProcessingRequestStatus = 'REQUESTED' | 'ACCEPTED' | 'CANCELED' | 'REJECTED' | 'EXPIRED'

/** `processing_quote.status` */
export type ProcessingQuoteStatus = 'SUBMITTED' | 'SELECTED' | 'REJECTED' | 'EXPIRED'

/**
 * `processing_job.status` — 2-Track 확정 정책.
 * Brand 배송 → Processor 수령 → 가공 → 결과 제출 → Brand 승인/반려
 */
export type ProcessingJobStatus =
  /** 요청 생성 완료, 가공비 결제 전. 결제 실패 시 이 상태에 머문다 */
  | 'CREATED'
  | 'PAID'
  /** 가공사가 일을 맡기로 했다. 소재 발송은 이 뒤에만 열린다 */
  | 'ACCEPTED'
  /** 가공사가 일을 거절했다. 가공비는 환불되고 소재 잠금이 풀린다 */
  | 'DECLINED'
  | 'SHIPPED'
  | 'RECEIVED'
  | 'PROCESSING'
  | 'RESULT_SUBMITTED'
  | 'RETURN_SHIPPED'
  | 'CANCELED'
  | 'CONFIRMED'
  | 'REJECTED'
  | 'EXPIRED'

export const PROCESS_TYPE_LABEL: Record<ProcessType, string> = {
  CLEANING: '세척',
  DISASSEMBLY: '해체',
  SORTING: '선별',
  CUTTING: '재단',
  SHREDDING: '분쇄',
  COLOR_PROCESSING: '염색',
  SURFACE_FINISHING: '표면 가공',
  REMANUFACTURING: '재제조',
}

// TODO(4번/Processing 담당): 견적·작업·투입/산출 소재 타입을 추가한다.
