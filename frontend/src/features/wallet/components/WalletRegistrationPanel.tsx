import { CheckCircle, Wallet } from '@phosphor-icons/react'
import { Button, ErrorMessage } from '../../../components/common/index.ts'
import { shortenAddress } from '../../../utils/address.ts'
import { ERROR_KIND } from '../../../utils/errors.ts'
import { useSwitchChainNotice, useWallet } from '../hooks/useWallet.ts'
import { useWalletRegistration } from '../hooks/useWalletRegistration.ts'

interface WalletRegistrationPanelProps {
  registeredAddress: string | null
}

const noticeText = {
  rejected: '연결을 취소했습니다.',
  'pending-in-wallet': 'MetaMask 창에서 요청을 확인해 주세요.',
  failed: '지갑에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.',
} as const

export function WalletRegistrationPanel({ registeredAddress }: WalletRegistrationPanelProps) {
  const wallet = useWallet()
  const registration = useWalletRegistration()
  const { message: switchMessage, report: reportSwitch } = useSwitchChainNotice()
  const isRegisteredWallet = Boolean(
    registeredAddress && wallet.address && registeredAddress.toLowerCase() === wallet.address.toLowerCase(),
  )

  if (wallet.status === 'unavailable') {
    return (
      <div className="company-wallet__actions">
        <a
          className="company-wallet__install-link"
          href="https://metamask.io/download/"
          target="_blank"
          rel="noreferrer noopener"
        >
          MetaMask 설치하기
        </a>
        <p>지갑 확장 프로그램이 감지되지 않았습니다.</p>
      </div>
    )
  }

  if (wallet.status !== 'connected') {
    return (
      <div className="company-wallet__actions">
        <Button onClick={wallet.connect} loading={wallet.status === 'connecting'}>
          {wallet.status === 'connecting' ? '연결하는 중…' : 'MetaMask 연결'}
        </Button>
        {wallet.notice ? <p>{noticeText[wallet.notice]}</p> : null}
      </div>
    )
  }

  return (
    <div className="company-wallet__actions">
      <div className="company-wallet__connected-address">
        <Wallet aria-hidden="true" />
        <div>
          <span>현재 MetaMask 계정</span>
          <code title={wallet.address ?? undefined}>
            {wallet.address ? shortenAddress(wallet.address) : ''}
          </code>
        </div>
      </div>

      {wallet.wrongChain ? (
        <div className="company-wallet__network-warning">
          <p>블록체인 기록에 맞는 연결 환경으로 변경한 뒤 지갑을 등록해 주세요.</p>
          <Button variant="secondary" onClick={() => void wallet.switchChain().then(reportSwitch)}>
            연결 환경 변경
          </Button>
          {switchMessage ? <span>{switchMessage}</span> : null}
        </div>
      ) : isRegisteredWallet ? (
        <p className="company-wallet__verified"><CheckCircle weight="fill" /> 등록된 지갑과 연결되었습니다.</p>
      ) : (
        <>
          <Button onClick={() => registration.mutate()} loading={registration.isPending}>
            {registeredAddress ? '이 지갑으로 변경' : '지갑 등록'}
          </Button>
          {registeredAddress ? (
            <p>새 지갑을 등록하면 기존 지갑은 비활성화되고 이력은 보존됩니다.</p>
          ) : (
            <p>지갑 확인 과정에서는 자산이 이동하지 않습니다.</p>
          )}
        </>
      )}

      {registration.error ? (
        registration.error.kind === ERROR_KIND.SIGNATURE_REJECTED ? (
          <p>{registration.error.message}</p>
        ) : (
          <ErrorMessage error={registration.error} />
        )
      ) : null}

      <Button variant="secondary" size="sm" onClick={wallet.disconnect}>사이트 연결 해제</Button>
    </div>
  )
}
