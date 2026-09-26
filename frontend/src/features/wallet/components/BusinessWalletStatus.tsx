import { CheckCircle, WarningCircle, Wallet } from '@phosphor-icons/react'
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMyWallets } from '../hooks/useMyWallets.ts'

export function BusinessWalletStatus() {
  const [isOpen, setIsOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const { activeWallet, isPending, isError, refetch } = useMyWallets()
  const isRegistered = Boolean(activeWallet)

  useEffect(() => {
    if (!isOpen) return

    function closeOnOutsideClick(event: MouseEvent) {
      if (!containerRef.current?.contains(event.target as Node)) setIsOpen(false)
    }

    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') setIsOpen(false)
    }

    document.addEventListener('mousedown', closeOnOutsideClick)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('mousedown', closeOnOutsideClick)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [isOpen])

  const statusLabel = isPending
    ? '지갑 상태 확인 중'
    : isError
      ? '지갑 상태 확인 실패'
      : isRegistered
        ? '지갑 연결됨'
        : '지갑 연결 안 됨'

  return (
    <div className="company-wallet-status" ref={containerRef}>
      <button
        className={`company-wallet-status__trigger${isRegistered ? ' company-wallet-status__trigger--registered' : ''}${isError ? ' company-wallet-status__trigger--error' : ''}`}
        type="button"
        aria-label={statusLabel}
        aria-expanded={isOpen}
        aria-controls="business-wallet-status-popover"
        onClick={() => setIsOpen((open) => !open)}
      >
        <Wallet weight={isRegistered ? 'fill' : 'regular'} aria-hidden="true" />
        {!isRegistered && !isPending ? (
          <span className="company-wallet-status__disconnected-mark" aria-hidden="true" />
        ) : null}
      </button>

      {isOpen ? (
        <div id="business-wallet-status-popover" className="company-wallet-status__popover" role="region" aria-label="지갑 연결 상태">
          <div className="company-wallet-status__message" aria-live="polite">
            {isRegistered ? <CheckCircle weight="fill" aria-hidden="true" /> : <WarningCircle aria-hidden="true" />}
            <div>
              <strong>{statusLabel}</strong>
              <span>
                {isPending
                  ? '등록된 지갑 정보를 확인하고 있습니다.'
                  : isError
                    ? '잠시 후 다시 확인해 주세요.'
                    : isRegistered
                      ? '거래 이력 기록 기능을 사용할 수 있습니다.'
                      : '마이페이지에서 지갑을 연결해 주세요.'}
              </span>
            </div>
          </div>
          {isError ? (
            <button className="company-wallet-status__retry" type="button" onClick={() => void refetch()}>
              다시 확인
            </button>
          ) : null}
          {!isPending ? (
            <Link to="/company/account#company-wallet" onClick={() => setIsOpen(false)}>
              {isRegistered ? '지갑 관리' : '연결하러 가기'}
            </Link>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
