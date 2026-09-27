import { isAppError } from '../../../utils/errors.ts'
import type { ChainRegistrationPhase } from '../hooks/useProcessingChainRegistration.ts'

const message = (error: unknown) => isAppError(error) || error instanceof Error ? error.message : null

/** 결제 직후 이어지는 온체인 등록의 진행 안내. 결제 완료 화면 두 곳(카카오페이 복귀·시연 결제)이 함께 쓴다. */
export function ProcessingChainRegistrationStatus({ phase, error, onRetry }: {
  phase: ChainRegistrationPhase
  error: unknown
  onRetry: () => void
}) {
  if (phase === 'IDLE') return null
  if (phase === 'REGISTERING') {
    return <p className="br-chain-registration">지갑에서 가공 작업 등록 서명을 승인해 주세요. 서명하면 투입 소재가 잠기고 가공업체가 수락할 수 있어요.</p>
  }
  if (phase === 'REGISTERED') {
    return <p className="br-chain-registration">가공업체가 수락하면 소재를 보낼 수 있어요.</p>
  }
  const detail = message(error)
  return (
    <div className="br-chain-registration br-chain-registration--failed" role="alert">
      <p>결제는 완료됐지만 가공 작업 등록 서명이 끝나지 않았어요.</p>
      {detail && <small>{detail}</small>}
      <button type="button" onClick={onRetry}>다시 서명하기</button>
    </div>
  )
}
