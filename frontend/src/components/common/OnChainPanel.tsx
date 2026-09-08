import type { ReactNode } from 'react'
import { env } from '../../config/env.ts'
import type { ChainLinkStatus } from '../../hooks/useOnChainLink.ts'
import type { AppError } from '../../utils/errors.ts'
import { Button } from './Button.tsx'
import { ErrorMessage } from './ErrorMessage.tsx'
import './on-chain-panel.css'

export interface OnChainPanelProps {
  title: string
  /** 무엇이 체인에 남는지 한 줄로. 사용자는 "왜 지갑이 뜨는지" 를 여기서 안다. */
  description: string
  /** 이미 연결된 온체인 번호. 있으면 버튼 대신 결과를 보여준다. */
  onChainId: number | null
  /**
   * 이 사람이 서명할 자격이 있는가 (등록자·판매자 본인인가).
   *
   * <b>false 여도 패널은 그린다.</b> "이 소재가 온체인에 있는가" 는 남이 봐도 알아야 하는
   * 사실이다 — 구매자가 판매 글을 보며 판단하는 근거이기도 하다. 감추는 것은 버튼뿐이다.
   */
  actionable: boolean
  /** 지금 서명할 수 있는가. 앞 단계가 안 끝났으면 false 와 함께 이유를 준다. */
  ready: boolean
  /** ready 가 false 인 이유. 버튼을 잠그기만 하면 사용자가 왜 못 누르는지 모른다. */
  blockedReason?: string
  status: ChainLinkStatus
  hash: string | null
  error: AppError | null
  isBusy: boolean
  isRejected: boolean
  onSign: () => void
  actionLabel: string
  children?: ReactNode
}

/**
 * 온체인 단계 하나를 보여주고 서명시킨다.
 *
 * **왜 오프체인과 분리해서 보여주는가** — 두 기록은 서로 다른 시점에 확정되고, 체인 쪽만
 * 실패할 수 있다. 한 버튼에 묶어 "저장" 이라고만 쓰면 사용자는 무엇이 남았는지 알 수 없고,
 * 실패했을 때 처음부터 다시 해야 하는지 이어서 하면 되는지도 알 수 없다.
 *
 * 서버 기록이 **먼저** 끝나 있고 체인은 그 위에 얹는 구조라, 여기서 실패해도 앞의 것은
 * 그대로 남는다. 그래서 다시 눌러도 된다 — 그 사실이 사용자에게 보여야 한다.
 */
export function OnChainPanel({
  title,
  description,
  onChainId,
  actionable,
  ready,
  blockedReason,
  status,
  hash,
  error,
  isBusy,
  isRejected,
  onSign,
  actionLabel,
  children,
}: OnChainPanelProps) {
  const done = onChainId !== null

  return (
    <section className="on-chain-panel">
      <header className="on-chain-panel__header">
        <h3>{title}</h3>
        <span className={`on-chain-panel__badge ${done ? 'is-done' : ''}`}>
          {done ? `온체인 #${onChainId}` : '미등록'}
        </span>
      </header>

      <p className="on-chain-panel__lead">{description}</p>

      {children}

      {!done && actionable && (
        <>
          {!ready && blockedReason && (
            <p className="on-chain-panel__blocked" role="status">
              {blockedReason}
            </p>
          )}

          <div className="on-chain-panel__actions">
            <Button onClick={onSign} disabled={!ready || isBusy} loading={isBusy}>
              {actionLabel}
            </Button>
            {/* 단계마다 사용자가 기다려야 하는 이유가 다르다. 그냥 스피너만 돌리면
                지갑을 봐야 하는지 기다리면 되는지 알 수 없다. */}
            {status === 'SIGNING' && <span className="on-chain-panel__hint">지갑에서 서명해 주세요</span>}
            {status === 'PENDING' && <span className="on-chain-panel__hint">블록 확정을 기다리는 중…</span>}
            {status === 'LINKING' && <span className="on-chain-panel__hint">결과를 기록하는 중…</span>}
          </div>

          {/* 서명 거부는 오류가 아니라 사용자의 선택이다. 붉은 배너를 띄우면 고장으로 읽힌다. */}
          {isRejected && <p className="on-chain-panel__hint">서명을 취소했습니다. 다시 시도할 수 있습니다.</p>}
          {error && !isRejected && <ErrorMessage error={error} />}
        </>
      )}

      {hash && <TransactionHash hash={hash} />}
    </section>
  )
}

/**
 * 트랜잭션 해시. 익스플로러가 설정돼 있으면 링크로 만든다.
 *
 * <p>전체 해시를 {@code title} 에 담아 둔다 — 잘린 값만 보여주면 사용자가 다른 곳에
 * 붙여 넣어 확인할 수 없다.
 */
export function TransactionHash({ hash }: { hash: string }) {
  const short = `${hash.slice(0, 10)}…${hash.slice(-8)}`

  if (!env.besuExplorerUrl) {
    return (
      <p className="on-chain-panel__hash" title={hash}>
        tx {short}
      </p>
    )
  }

  return (
    <p className="on-chain-panel__hash">
      tx{' '}
      <a
        href={`${env.besuExplorerUrl}/tx/${hash}`}
        target="_blank"
        rel="noreferrer noopener"
        title={hash}
      >
        {short}
      </a>
    </p>
  )
}
