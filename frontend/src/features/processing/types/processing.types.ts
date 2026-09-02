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

/** `processing_request.status` */
export type ProcessingRequestStatus = 'QUOTING' | 'SELECTED' | 'CANCELED' | 'EXPIRED'

/** `processing_quote.status` */
export type ProcessingQuoteStatus = 'SUBMITTED' | 'SELECTED' | 'REJECTED' | 'EXPIRED'

/**
 * `processing_job.status` — 2-Track 확정 정책.
 * Brand 배송 → Processor 수령 → 가공 → 결과 제출 → Brand 승인/반려
 */
export type ProcessingJobStatus =
  | 'PAID'
  | 'SHIPPED'
  | 'RECEIVED'
  | 'PROCESSING'
  | 'RESULT_SUBMITTED'
  | 'CONFIRMED'
  | 'REJECTED'

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
