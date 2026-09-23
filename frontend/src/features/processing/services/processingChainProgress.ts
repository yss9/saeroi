import { getProcessingChainStatus } from './processingContractService.ts'

/**
 * 가공 작업이 <b>체인에서 어느 단계까지 갔는가.</b>
 *
 * <h2>왜 필요한가 — 체인이 앞서면 되돌릴 방법이 없다</h2>
 *
 * 각 단계는 **서명이 먼저, 서버가 나중**이다. 그 사이에 서버 호출이 실패하면(네트워크 끊김,
 * 토큰 만료, 서버 재시작) 체인은 다음 단계로 갔는데 DB 는 그대로 남는다. 다시 누르면
 * 컨트랙트가 `require` 를 어겨 `Processing: invalid status` 로 거절하고, **그 뒤의 서버
 * 호출까지 못 간다** — DB 가 영원히 따라잡지 못한다. 누를수록 같은 오류만 난다.
 *
 * 실제로 그렇게 막혔다. `startProcessing` 이 체인에 들어간 뒤 서버 보고가 빠져, 체인은
 * `PROCESSING` 인데 DB 는 `RECEIVED` 였다. 업체는 "가공 시작" 을 누를 때마다
 * `Processing: invalid status` 만 봤다.
 *
 * 그래서 누르기 전에 체인에 물어보고, **이미 지난 단계면 서명을 건너뛰고 서버만 채운다.**
 *
 * <h2>번호 크기로 비교하지 않는다</h2>
 *
 * 상태 번호는 진행 순서가 아니다. `DECLINED`(9)·`CANCELED`(10)가 `SHIPPED`(3)보다 큰데 그
 * 둘은 발송이 일어난 적 없다. 크기로 비교하면 거절된 작업에 "이미 지났다" 고 답해 서버를
 * 앞으로 밀어 준다. 서버의 `ChainProcessingStatusReader` 도 같은 이유로 집합을 쓴다.
 */
const PASSED: Record<ProcessingChainStep, ReadonlySet<number>> = {
  //  3 SHIPPED  4 RECEIVED  5 PROCESSING  6 RESULT_SUBMITTED  7 CONFIRMED
  //  8 RESULT_REJECTED  11 RETURN_SHIPPED  12 REJECTED_INPUT_RETURNED
  SHIPPED: new Set([3, 4, 5, 6, 7, 8, 11, 12]),
  RECEIVED: new Set([4, 5, 6, 7, 8, 11, 12]),
  PROCESSING: new Set([5, 6, 7, 8, 11, 12]),
}

export type ProcessingChainStep = 'SHIPPED' | 'RECEIVED' | 'PROCESSING'

/**
 * 체인이 이 단계를 <b>이미 지났는가.</b>
 *
 * 못 읽으면 `false` 다 — 읽기 실패로 서명을 막지 않는다. 이미 지난 상태라면 컨트랙트가
 * 거절하고, 그 실패는 사용자에게 그대로 보인다. 모르는 것을 "지났다" 로 답해 서버만 앞으로
 * 미는 것이 더 나쁘다.
 */
export async function chainAlreadyPassed(
  chainJobId: number,
  step: ProcessingChainStep,
): Promise<boolean> {
  try {
    return PASSED[step].has(await getProcessingChainStatus(chainJobId))
  } catch {
    return false
  }
}
