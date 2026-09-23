import type { PendingAction } from '../types/agent.types.ts'

/**
 * 이 카드를 <b>오른쪽 패널이</b> 그리는가.
 *
 * 결제·서명 카드는 대화가 흐르면 떠내려간다. 소재를 세 건 사면 결제 카드가 세 장 한꺼번에
 * 생기는데, 그 뒤로 모델이 계속 말하면서 카드가 위로 밀려 담당자가 도로 스크롤해야 했다 —
 * 실제로 챗봇이 "울 원단의 결제 카드가 여전히 떠 있습니다" 라고 담당자를 위로 안내했다.
 * 오른쪽 패널은 따로 스크롤되므로 거기 두면 떠내려가지 않는다.
 *
 * **생산 온체인 기록만 대화에 남긴다.** 네 단계 진행을 보여 주는 패널이라 280px 폭에
 * 넣으면 읽을 수 없다.
 *
 * 좁은 화면(780px 이하)에서는 오른쪽 패널이 대화 **아래**로 내려간다. 그때 패널에 두면
 * 대화 전체를 지나쳐야 카드에 닿으므로 지금처럼 대화 안에 둔다.
 */
export function pinnedToPanel(action: PendingAction, wide: boolean): boolean {
  if (!wide || action.status !== 'PENDING') return false
  return action.toolName !== 'record_production_chain'
}

