import type { ProcessingJobStatus, ProcessingRequestStatus } from '../types/processing.types.ts'

/**
 * 브랜드가 읽는 "이 가공은 지금 어디까지 왔는가".
 *
 * **요청 상태와 작업 상태는 하나로 읽혀야 한다.** 견적을 수락하면 요청은 `SELECTED` 에서
 * 더 움직이지 않고, 그 뒤의 일(결제·발송·가공·반환)은 전부 작업(`processing_job`)에 적힌다.
 * 둘을 따로 보여주면 브랜드는 "견적 수락" 에 멈춘 목록을 보며 가공이 끝난 줄 모른다.
 *
 * 그래서 **작업이 있으면 작업이 말하고, 없으면 요청이 말한다.**
 */
const REQUEST_LABEL: Record<ProcessingRequestStatus, string> = {
  QUOTING: '견적 대기',
  SELECTED: '견적 수락',
  CANCELED: '취소',
  REJECTED: '거절',
  EXPIRED: '만료',
}

/**
 * 작업 상태 → 브랜드의 말.
 *
 * 이름은 **브랜드가 다음에 할 일**을 기준으로 골랐다. `RETURN_SHIPPED` 를 "반환 배송 중"
 * 이라 적으면 기다리는 상태로 읽히는데, 실제로는 물건이 도착하는 대로 수령을 확인해야
 * 소재가 손에 들어온다 — 그 확인이 없으면 산출 소재는 발행되지 않는다.
 */
const JOB_LABEL: Record<ProcessingJobStatus, string> = {
  CREATED: '결제 대기',
  PAID: '발송 대기',
  SHIPPED: '배송 중',
  RECEIVED: '가공 대기',
  PROCESSING: '가공 중',
  RESULT_SUBMITTED: '가공 완료',
  RETURN_SHIPPED: '수령 확인 대기',
  CONFIRMED: '수령 완료',
  REJECTED: '보완 요청',
  EXPIRED: '만료',
  CANCELED: '반환 종료',
}

/** 작업 상태 한 줄. 작업이 있는 화면(요청 상세의 진행 칸)이 쓴다. */
export function jobLabel(jobStatus: ProcessingJobStatus): string {
  return JOB_LABEL[jobStatus]
}

export function progressLabel(
  requestStatus: ProcessingRequestStatus,
  jobStatus?: ProcessingJobStatus,
): string {
  return jobStatus === undefined ? REQUEST_LABEL[requestStatus] : JOB_LABEL[jobStatus]
}

/** 상태 색을 고르는 이름. 라벨과 같은 근거(작업이 있으면 작업)를 쓴다. */
export function progressTone(
  requestStatus: ProcessingRequestStatus,
  jobStatus?: ProcessingJobStatus,
): string {
  return (jobStatus ?? requestStatus).toLowerCase()
}

/** 브랜드가 지금 수령을 확인할 수 있는가. 서버도 이 상태에서만 받는다. */
export function canConfirmReceipt(jobStatus: ProcessingJobStatus | undefined): boolean {
  return jobStatus === 'RETURN_SHIPPED'
}
