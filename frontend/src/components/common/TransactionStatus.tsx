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
  SIGNING: 'MetaMask에서 서명을 완료해 주세요.',
  PENDING: '트랜잭션이 블록에 기록되기를 기다리는 중입니다.',
  CONFIRMED: '블록체인에 기록되었습니다.',
  FAILED: '트랜잭션을 완료하지 못했습니다.',
}

/**
 * 블록체인 트랜잭션 진행 표시.
 *
 * API 로딩과 달리 이 흐름은 사용자가 **지갑 창에서 직접 행동해야** 넘어가고,
 * PENDING 은 수 초~수십 초가 걸린다. 그래서 일반 스피너와 구분해 따로 둔다.
 */
export function TransactionStatus({
  status,
  hash,
  error,
  explorerBaseUrl,
}: TransactionStatusProps) {
  if (status === 'IDLE') return null

  return (
    <div className="ui-tx" role="status" aria-live="polite">
      <StatusBadge tone={TONE[status]}>{status}</StatusBadge>

      <p className="ui-tx__message">{status === 'FAILED' && error ? error.message : MESSAGE[status]}</p>

      {status === 'SIGNING' || status === 'PENDING' ? <Loading label={MESSAGE[status]} /> : null}

      {/* 해시는 PENDING 부터 존재한다. 실패해도 해시가 있으면 보여준다 —
          사용자가 익스플로러에서 원인을 확인할 수 있어야 한다. */}
      {hash && (
        <p className="ui-tx__hash">
          {explorerBaseUrl ? (
            <a href={`${explorerBaseUrl}/tx/${hash}`} target="_blank" rel="noreferrer noopener">
              {hash}
            </a>
          ) : (
            hash
          )}
        </p>
      )}
    </div>
  )
}
