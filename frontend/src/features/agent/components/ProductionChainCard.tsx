import { useEffect } from 'react'
import { useCompleteAction, useRejectAction } from '../hooks/useAgentConversation.ts'
import { StatusOrActions } from './PaymentCard.tsx'
import { ProductionChainPanel } from '../../production/components/ProductionChainPanel.tsx'
import { useProductionBatch } from '../../production/hooks/useProductionBatch.ts'
import { useProductionChainStatus } from '../../production/hooks/useProductionChainStatus.ts'
import type { PendingAction } from '../types/agent.types.ts'

/**
 * 생산 온체인 기록 카드. 서명이 네 번이다.
 *
 * <b>서명 흐름을 다시 만들지 않고 생산 화면의 패널을 그대로 띄운다.</b> 네 단계는 컨트랙트가
 * 순서를 강제하고, 다음에 할 단계는 `stepDone` 이 아니라 **체인 상태**로 정해야 한다 —
 * 트랜잭션은 성공했는데 서버 보고가 실패하면 서버 기준으로는 영원히 "아직 안 함" 이고, 그걸
 * 믿고 다시 서명하면 컨트랙트가 revert 해 막다른 길이 된다. 그 판정은 이미
 * `ProductionChainPanel` 이 하고 있으므로, 에이전트용으로 한 벌 더 만들면 두 구현이 어긋나는
 * 순간 어느 쪽이 정본인지 알 수 없게 된다.
 *
 * 그래서 이 카드가 하는 일은 셋뿐이다 — 패널을 띄우고, 체인이 "다 됐다" 고 말하면 카드를
 * 닫고, 담당자가 나중에 하겠다면 거절로 닫는다.
 */
export function ProductionChainCard({ action }: { action: PendingAction }) {
  const complete = useCompleteAction()
  const reject = useRejectAction()

  const batchId = readBatchId(action.payloadJson)
  const batch = useProductionBatch(batchId)
  const chain = useProductionChainStatus(batch.data?.chainBatchId ?? null)

  // 네 단계가 모두 끝났는가. 3단계까지 가면 컨트랙트 상태가 CONFIRMED 가 되고, 4단계는
  // 개체가 연결됐는지로 판단한다 — 상태 값만으로는 4단계를 구분할 수 없다.
  const allDone =
    chain.data?.status === 'CONFIRMED' && chain.data.linkedItemCount > 0

  // 담당자가 마지막 서명을 마치는 순간 카드를 닫는다. 버튼을 한 번 더 누르게 하면,
  // 이미 끝난 일을 두고 "확인" 을 누르는 셈이라 무엇을 확인하는지 알 수 없다.
  useEffect(() => {
    if (allDone && action.status === 'PENDING' && !complete.isPending) {
      void complete.mutateAsync({ actionId: action.id }).catch(() => {
        // 닫기에 실패해도 서명은 이미 체인에 남았다. 카드가 남아 있을 뿐이라 막지 않는다.
      })
    }
  }, [allDone, action.status, action.id, complete])

  if (batchId === null) {
    return (
      <p className="agent-card agent-card--sign agent-card__error">
        어느 생산을 기록할지 알 수 없습니다. 대화에서 다시 요청해 주세요.
      </p>
    )
  }

  return (
    <div className="agent-card agent-card--sign">
      <p className="agent-card__title">블록체인 기록 — 서명 4번</p>
      <p className="agent-card__raw">{action.summary}</p>

      {batch.isPending && <p className="agent-card__detail">생산 정보를 불러오는 중…</p>}
      {batch.isError && (
        <p className="agent-card__error">생산 정보를 불러오지 못했습니다.</p>
      )}
      {batch.data && <ProductionChainPanel batch={batch.data} />}

      <StatusOrActions
        action={action}
        disabled
        busyLabel="서명 진행 중…"
        confirmLabel="서명 진행 중…"
        onConfirm={() => {}}
        isBusy={complete.isPending}
        onReject={() => void reject.mutateAsync(action.id)}
      />
    </div>
  )
}

/** 카드 payload 에서 배치 id 를 꺼낸다. 모델이 만든 JSON 이라 깨질 수 있다. */
function readBatchId(payloadJson: string): number | null {
  try {
    const parsed = JSON.parse(payloadJson) as { batchId?: unknown }
    return typeof parsed.batchId === 'number' ? parsed.batchId : null
  } catch {
    return null
  }
}
