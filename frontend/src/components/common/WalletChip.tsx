import { shortenAddress } from '../../utils/address.ts'

export interface WalletChipProps {
  /** EIP-55 체크섬 주소. 미연결이면 null */
  address: string | null
  /** 기대 체인과 다른 네트워크에 연결된 상태 */
  wrongChain?: boolean
  onClick?: () => void
}


/**
 * 헤더 등에 놓는 지갑 상태 표시.
 *
 * 이 컴포넌트는 지갑 상태를 **직접 구독하지 않는다** — props 로 받는다.
 * 공통 컴포넌트가 특정 스토어에 묶이면 테스트와 재사용이 어려워지기 때문이다.
 * 스토어 연결은 features/wallet 쪽 컨테이너가 담당한다.
 */
export function WalletChip({ address, wrongChain = false, onClick }: WalletChipProps) {
  if (!address) {
    return (
      <button className="ui-wallet-chip ui-wallet-chip--empty" onClick={onClick}>
        지갑 연결
      </button>
    )
  }

  return (
    <button
      className={`ui-wallet-chip${wrongChain ? ' ui-wallet-chip--warning' : ''}`}
      onClick={onClick}
      title={address}
    >
      <span className="ui-wallet-chip__address">{shortenAddress(address)}</span>
      {wrongChain && <span className="ui-wallet-chip__warning">네트워크 확인</span>}
    </button>
  )
}
