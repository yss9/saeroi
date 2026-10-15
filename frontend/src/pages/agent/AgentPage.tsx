import { AgentChat } from '../../features/agent/components/AgentChat.tsx'
import './agent.css'

/**
 * AI 어시스턴트 대화 화면.
 *
 * <b>별도 창으로 열린다.</b> 담당자가 기존 화면(소재 목록, 가공 요청)을 보면서 대화할 수
 * 있어야 하기 때문이다. `AgentLauncher` 가 이름 있는 창으로 띄우므로 버튼을 여러 번 눌러도
 * 창은 하나만 유지된다.
 *
 * 그래서 이 화면은 사이드바나 셸을 두지 않는다 — 좁은 창을 옆에 붙여 두고 쓰는 용도다.
 */
export default function AgentPage() {
  return (
    <main className="agent-page">
      <AgentChat />
    </main>
  )
}
