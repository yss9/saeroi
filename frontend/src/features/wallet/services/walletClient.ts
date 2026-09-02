// MetaMask 연결 로직. React 에 의존하지 않는다.
//
// provider 를 인자로 받는 이유는 두 가지다.
//   1) 테스트에서 가짜 객체를 끼워 실제 확장 없이 전 분기를 검증할 수 있다.
//   2) 나중에 EIP-6963(다중 지갑 탐색)으로 provider 를 고르게 바뀌어도
//      이 파일은 그대로 두고 주입부만 바꾸면 된다.

import { getAddress } from 'ethers'
import {
  INITIAL_WALLET_STATE,
  RPC_ERROR,
  type EthereumProvider,
  type WalletNotice,
  type WalletState,
} from '../types/wallet.types.ts'

/** `wallet_switchEthereumChain` 시도 결과. UI 가 안내 문구를 고르는 데 쓴다. */
export type SwitchChainResult =
  | 'switched'
  /** 지갑에 없던 체인이라 새로 등록한 뒤 전환했다 */
  | 'added'
  /** 등록이 필요한데 RPC URL 이 없어 시도조차 못 했다 (설정 미완) */
  | 'missing-rpc-url'
  | 'rejected'
  | 'failed'

export interface WalletClient {
  getState(): WalletState
  /**
   * 상태 변화를 구독한다. 반환값을 호출하면 해제된다.
   *
   * 지갑 이벤트(accountsChanged/chainChanged) 리스너는 **여기서** 붙고 뗀다.
   * 첫 구독자가 생기면 붙이고 마지막 구독자가 사라지면 뗀다. 클라이언트를
   * 만드는 것만으로는 provider 에 아무것도 등록하지 않는다.
   *
   * 처음에는 생성 시점에 등록하고 별도 destroy() 로 뗐는데, 그러면 한 번 뗀 뒤
   * 다시 붙일 방법이 없었다. React StrictMode 는 개발 모드에서 마운트→언마운트→
   * 재마운트를 흉내내므로 재마운트 이후 지갑 이벤트가 영영 오지 않았다.
   * (연결·복원은 직접 호출이라 멀쩡해 보여서 더 잘 안 드러났다.)
   */
  subscribe(listener: (state: WalletState) => void): () => void
  /**
   * 팝업 없이 이미 승인된 계정을 복원한다. 페이지 로드 시 한 번 부른다.
   * 이걸 하지 않으면 새로고침할 때마다 사용자에게 재연결을 요구하게 된다.
   */
  restore(): Promise<void>
  /** 사용자 클릭에 반응해 연결한다. MetaMask 팝업이 뜬다. */
  connect(): Promise<void>
  /**
   * 로컬 상태만 비운다.
   * dapp 이 지갑 연결을 강제로 끊는 표준 방법은 존재하지 않으므로,
   * 실제 권한 회수는 사용자가 MetaMask 에서 직접 해야 한다.
   */
  disconnect(): void
  switchChain(targetChainId: string, rpcUrl: string | null): Promise<SwitchChainResult>
}

/** provider 가 던진 값에서 EIP-1193 에러 코드를 꺼낸다. 코드가 없으면 null. */
function errorCode(error: unknown): number | null {
  if (typeof error !== 'object' || error === null) return null
  const code = (error as { code?: unknown }).code
  return typeof code === 'number' ? code : null
}

function noticeFor(error: unknown): WalletNotice {
  switch (errorCode(error)) {
    case RPC_ERROR.USER_REJECTED:
      return 'rejected'
    case RPC_ERROR.ALREADY_PENDING:
      return 'pending-in-wallet'
    default:
      return 'failed'
  }
}

/**
 * 계정 목록의 첫 주소를 EIP-55 체크섬 형태로 정규화한다.
 * 지갑이 소문자로 주는 경우가 있어 표시·비교 전에 반드시 통과시킨다.
 * 목록이 비었거나 주소 형식이 아니면 null.
 */
function firstAddress(accounts: unknown): string | null {
  if (!Array.isArray(accounts) || accounts.length === 0) return null
  const raw = accounts[0]
  if (typeof raw !== 'string') return null
  try {
    return getAddress(raw)
  } catch {
    return null
  }
}

export function createWalletClient(provider: EthereumProvider | null): WalletClient {
  let state: WalletState = provider
    ? { ...INITIAL_WALLET_STATE, status: 'disconnected' }
    : { ...INITIAL_WALLET_STATE }

  const listeners = new Set<(state: WalletState) => void>()

  function emit(patch: Partial<WalletState>): void {
    state = { ...state, ...patch }
    for (const listener of listeners) listener(state)
  }

  async function readChainId(): Promise<string | null> {
    if (!provider) return null
    try {
      const chainId = await provider.request({ method: 'eth_chainId' })
      return typeof chainId === 'string' ? chainId : null
    } catch {
      // 체인 조회 실패가 연결 자체를 실패로 만들 이유는 없다. 표시만 비운다.
      return null
    }
  }

  // ── 지갑이 밀어주는 이벤트 ────────────────────────────────
  // accountsChanged 는 계정 전환뿐 아니라 "사용자가 사이트 연결을 끊었을 때"도 온다.
  // 그때는 빈 배열이 오므로 연결 해제로 처리해야 한다.
  const onAccountsChanged = (...args: never[]): void => {
    const address = firstAddress(args[0])
    if (address) {
      emit({ status: 'connected', address, notice: null })
    } else {
      emit({ status: 'disconnected', address: null, notice: null })
    }
  }

  const onChainChanged = (...args: never[]): void => {
    const chainId = args[0]
    emit({ chainId: typeof chainId === 'string' ? chainId : null })
  }

  // 리스너 등록은 구독 여부에 따라 켜고 끈다. 생성만으로는 아무것도 등록하지 않으므로
  // 클라이언트가 실수로 두 번 만들어져도 버려진 쪽이 이벤트를 가로채지 않는다.
  let attached = false

  function attach(): void {
    if (attached || !provider) return
    provider.on('accountsChanged', onAccountsChanged)
    provider.on('chainChanged', onChainChanged)
    attached = true
  }

  function detach(): void {
    if (!attached || !provider) return
    provider.removeListener('accountsChanged', onAccountsChanged)
    provider.removeListener('chainChanged', onChainChanged)
    attached = false
  }

  return {
    getState: () => state,

    subscribe(listener) {
      listeners.add(listener)
      if (listeners.size === 1) attach()
      return () => {
        listeners.delete(listener)
        if (listeners.size === 0) detach()
      }
    },

    async restore() {
      if (!provider) return
      try {
        // eth_accounts 는 팝업을 띄우지 않는다. 이미 승인된 계정만 돌려준다.
        const accounts = await provider.request({ method: 'eth_accounts' })
        const address = firstAddress(accounts)
        if (!address) return
        emit({ status: 'connected', address, chainId: await readChainId(), notice: null })
      } catch {
        // 복원은 사용자가 요청한 동작이 아니므로 조용히 실패한다.
      }
    },

    async connect() {
      if (!provider) return
      // 팝업이 떠 있는 동안 버튼을 다시 누르면 provider 가 -32002 를 던진다.
      // 여기서 먼저 막아 불필요한 요청 자체를 만들지 않는다.
      if (state.status === 'connecting') {
        emit({ notice: 'pending-in-wallet' })
        return
      }
      emit({ status: 'connecting', notice: null })
      try {
        const accounts = await provider.request({ method: 'eth_requestAccounts' })
        const address = firstAddress(accounts)
        if (!address) {
          emit({ status: 'disconnected', address: null, notice: 'failed' })
          return
        }
        emit({ status: 'connected', address, chainId: await readChainId(), notice: null })
      } catch (error) {
        // 사용자의 거부(4001)는 에러가 아니라 정상 흐름이다. 예외를 밖으로 던지지 않는다.
        emit({ status: 'disconnected', address: null, notice: noticeFor(error) })
      }
    },

    disconnect() {
      emit({ status: 'disconnected', address: null, notice: null })
    },

    async switchChain(targetChainId, rpcUrl) {
      if (!provider) return 'failed'
      try {
        await provider.request({
          method: 'wallet_switchEthereumChain',
          params: [{ chainId: targetChainId }],
        })
        return 'switched'
      } catch (error) {
        if (errorCode(error) !== RPC_ERROR.UNRECOGNIZED_CHAIN) {
          return errorCode(error) === RPC_ERROR.USER_REJECTED ? 'rejected' : 'failed'
        }
        // 지갑에 없는 체인이다. 등록하려면 RPC URL 이 필요한데,
        // VITE_BESU_RPC_URL 이 아직 비어 있으면 여기서 멈춘다.
        if (!rpcUrl) return 'missing-rpc-url'
        try {
          await provider.request({
            method: 'wallet_addEthereumChain',
            params: [
              {
                chainId: targetChainId,
                chainName: 'SSAFY Besu',
                rpcUrls: [rpcUrl],
                nativeCurrency: { name: 'Ether', symbol: 'ETH', decimals: 18 },
              },
            ],
          })
          return 'added'
        } catch (addError) {
          return errorCode(addError) === RPC_ERROR.USER_REJECTED ? 'rejected' : 'failed'
        }
      }
    },
  }
}

