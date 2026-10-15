import type { TransactionState } from '../../hooks/useBlockchainTransaction.ts'
import { Loading } from './Loading.tsx'
import { StatusBadge, type StatusTone } from './StatusBadge.tsx'

export interface TransactionStatusProps extends Pick<TransactionState, 'status' | 'hash' | 'error'> {
  /** 블록 익스플로러 주소. 없으면 해시를 텍스트로만 보여준다. */
  explorerBaseUrl?: string | null
}

const TONE: Record<TransactionState['status'], StatusTone> = {
  IDLE: 'neutral',
  SIGNING: 'info',
  PENDING: 'warning',
  CONFIRMED: 'success',
  FAILED: 'danger',
}

const MESSAGE: Record<TransactionState['status'], string> = {
  IDLE: '',
  SIGNING: '지갑에서 요청을 확인해 주세요.',
  PENDING: '이력을 저장하는 중입니다.',
  CONFIRMED: '이력이 저장되었습니다.',
  FAILED: '이력을 저장하지 못했습니다.',
}

const STATUS_LABEL: Record<TransactionState['status'], string> = {
  IDLE: '',
  SIGNING: '확인 중',
  PENDING: '저장 중',
  CONFIRMED: '저장 완료',
  FAILED: '저장 실패',
}

/**
 * 블록체인 트랜잭션 진행 표시.
 *
 * API 로딩과 달리 이 흐름은 사용자가 **지갑 창에서 직접 행동해야** 넘어가고,
 * PENDING 은 수 초~수십 초가 걸린다. 그래서 일반 스피너와 구분해 따로 둔다.
 */
export function TransactionStatus({
  status,
  error,
}: TransactionStatusProps) {
  if (status === 'IDLE') return null

  return (
    <div className="ui-tx" role="status" aria-live="polite">
      <StatusBadge tone={TONE[status]}>{STATUS_LABEL[status]}</StatusBadge>

      <p className="ui-tx__message">{status === 'FAILED' && error ? error.message : MESSAGE[status]}</p>

      {status === 'SIGNING' || status === 'PENDING' ? <Loading label={MESSAGE[status]} /> : null}

    </div>
  )
}
