import { shortenAddress } from '../../../utils/address.ts'
import { useSwitchChainNotice, useWallet } from '../hooks/useWallet.ts'
import type { WalletNotice } from '../types/wallet.types.ts'

const NOTICE_TEXT: Record<WalletNotice, string> = {
  rejected: '연결을 취소했습니다.',
  // 가장 흔한 실사용 상황: 팝업이 이미 열려 있는데 버튼을 다시 누른 경우.
  'pending-in-wallet': 'MetaMask 창에서 요청을 확인해 주세요.',
  failed: '지갑에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.',
}

export function WalletButton() {
  const wallet = useWallet()
  const { message: switchMessage, report } = useSwitchChainNotice()

  if (wallet.status === 'unavailable') {
    return (
      <div className="wallet">
        <a
          className="wallet__button"
          href="https://metamask.io/download/"
          target="_blank"
          rel="noreferrer noopener"
        >
          MetaMask 설치하기
        </a>
        <p className="wallet__note">지갑 확장 프로그램이 감지되지 않았습니다.</p>
      </div>
    )
  }

  if (wallet.status !== 'connected') {
    const connecting = wallet.status === 'connecting'
    return (
      <div className="wallet">
        <button className="wallet__button" onClick={wallet.connect} disabled={connecting}>
          {connecting ? '연결하는 중…' : '지갑 연결'}
        </button>
        {wallet.notice && <p className="wallet__note">{NOTICE_TEXT[wallet.notice]}</p>}
      </div>
    )
  }

  return (
    <div className="wallet">
      <div className="wallet__row">
        <span className="wallet__address" title={wallet.address ?? undefined}>
          {wallet.address ? shortenAddress(wallet.address) : ''}
        </span>
        <button className="wallet__button wallet__button--ghost" onClick={wallet.disconnect}>
          연결 해제
        </button>
      </div>

      <p className="wallet__note">
        네트워크 {wallet.chainId ?? '확인 불가'}
        {/* 기대 체인 ID(VITE_BESU_CHAIN_ID)가 설정되기 전에는 이 경고 자체가 뜨지 않는다. */}
        {wallet.wrongChain && ` — ${wallet.expectedChainId} 이어야 합니다`}
      </p>

      {wallet.wrongChain && (
        <button
          className="wallet__button wallet__button--ghost"
          onClick={() => void wallet.switchChain().then(report)}
        >
          네트워크 전환
        </button>
      )}
      {switchMessage && <p className="wallet__note">{switchMessage}</p>}

      {/* dapp 이 지갑 연결을 강제로 끊는 표준 방법은 없다. 사용자가 오해하지 않게 명시한다. */}
      <p className="wallet__note wallet__note--muted">
        완전한 연결 해제는 MetaMask의 &lsquo;연결된 사이트&rsquo;에서 할 수 있습니다.
      </p>
    </div>
  )
}
