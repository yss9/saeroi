import type { ProcessingJobStatus, ProcessingRequestStatus } from '../types/processing.types.ts'

/**
 * 브랜드가 읽는 "이 가공은 지금 어디까지 왔는가".
 *
 * **요청 상태와 작업 상태는 하나로 읽혀야 한다.** 요청은 만들어진 뒤 `REQUESTED` 에서
 * 더 움직이지 않고, 그 뒤의 일(결제·수락·발송·가공·반환)은 전부 작업(`processing_job`)에
 * 적힌다. 둘을 따로 보여주면 브랜드는 멈춘 목록을 보며 가공이 끝난 줄 모른다.
 *
 * 그래서 **작업이 있으면 작업이 말하고, 없으면 요청이 말한다.**
 */
const REQUEST_LABEL: Record<ProcessingRequestStatus, string> = {
  REQUESTED: '결제 대기',
  ACCEPTED: '가공사 수락',
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
  PAID: '수락 대기',
  ACCEPTED: '발송 대기',
  DECLINED: '가공사 거절',
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

/**
 * 목록을 훑는 단위 — **지금 누구 차례인가.**
 *
 * 상태는 열다섯 가지지만 브랜드가 목록에서 찾는 것은 둘뿐이다. 내가 손대야 하는 건과
 * 기다리면 되는 건. 그래서 버튼은 상태가 아니라 이 네 묶음으로 둔다 — 어느 줄이 무슨
 * 상태인지는 상태 칸이 이미 말한다.
 */
export type ProcessingPhase = 'MY_TURN' | 'IN_PROGRESS' | 'DONE' | 'CLOSED'

/** 브랜드가 직접 눌러야 다음으로 넘어가는 작업 상태. */
const MY_TURN_JOB: readonly ProcessingJobStatus[] = [
  'CREATED', // 결제 대기
  'ACCEPTED', // 가공사가 수락했다 — 이제 소재를 보낸다
  'RETURN_SHIPPED', // 수령 확인 대기 — 확인해야 산출 소재가 발행된다
]

/** 더 손댈 것이 없는 작업 상태. */
const CLOSED_JOB: readonly ProcessingJobStatus[] = ['REJECTED', 'DECLINED', 'EXPIRED', 'CANCELED']

export function phaseOf(
  requestStatus: ProcessingRequestStatus,
  jobStatus: ProcessingJobStatus | undefined,
): ProcessingPhase {
  if (jobStatus === undefined) {
    // 요청이 만들어지면 곧바로 작업이 생기므로 여기 오는 일은 드물다 — 결제가 남아 있다.
    if (requestStatus === 'REQUESTED') return 'MY_TURN'
    if (requestStatus === 'ACCEPTED') return 'IN_PROGRESS'
    return 'CLOSED'
  }
  if (MY_TURN_JOB.includes(jobStatus)) return 'MY_TURN'
  if (CLOSED_JOB.includes(jobStatus)) return 'CLOSED'
  return jobStatus === 'CONFIRMED' ? 'DONE' : 'IN_PROGRESS'
}

/** 목록 순서. 내 차례가 맨 위다 — 대부분은 위 몇 줄만 보고 끝난다. */
export const PHASE_ORDER: readonly ProcessingPhase[] = [
  'MY_TURN',
  'IN_PROGRESS',
  'DONE',
  'CLOSED',
]
