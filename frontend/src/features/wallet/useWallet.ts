// metamask.ts 의 클라이언트를 React 에 붙이는 얇은 계층.
// 지갑 로직은 여기 두지 않는다 — 전부 metamask.ts 에 있고 여기서는 배선만 한다.

import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react'
import { env } from '../../config/env.ts'
import { createWalletClient, type SwitchChainResult } from './metamask.ts'
import type { EthereumProvider, WalletState } from './types.ts'

declare global {
  interface Window {
    ethereum?: EthereumProvider
  }
}

/**
 * 주입된 provider 를 가져온다.
 *
 * 지갑 확장을 여러 개 설치한 사용자는 `window.ethereum` 을 놓고 경합한다.
 * 그 경우를 제대로 풀려면 EIP-6963(다중 provider 탐색)이 필요한데, 지금은 과잉이라
 * 이 함수 하나만 나중에 교체할 수 있게 분리해 둔다.
 */
function getInjectedProvider(): EthereumProvider | null {
  return typeof window === 'undefined' ? null : (window.ethereum ?? null)
}

export interface UseWalletResult extends WalletState {
  /** 연결된 체인이 기대값과 다른가. 기대값(VITE_BESU_CHAIN_ID)이 없으면 항상 false. */
  wrongChain: boolean
  /** 기대 체인 ID (16진수). 미설정이면 null. */
  expectedChainId: string | null
  connect: () => void
  disconnect: () => void
  switchChain: () => Promise<SwitchChainResult>
}

export function useWallet(): UseWalletResult {
  const client = useMemo(() => createWalletClient(getInjectedProvider()), [])
  // 컴포넌트가 사라질 때 지갑 이벤트 리스너를 반드시 떼어낸다.
  useEffect(() => () => client.destroy(), [client])

  // getState 는 상태가 바뀔 때만 새 객체를 만들므로 참조 비교로 안전하게 구독할 수 있다.
  const state = useSyncExternalStore(client.subscribe, client.getState, client.getState)

  // 페이지 로드 시 한 번만, 팝업 없이 기존 승인 계정을 복원한다.
  // StrictMode 는 개발 모드에서 effect 를 두 번 실행하므로 ref 로 중복을 막는다.
  const restored = useRef(false)
  useEffect(() => {
    if (restored.current) return
    restored.current = true
    void client.restore()
  }, [client])

  const connect = useCallback(() => void client.connect(), [client])
  const disconnect = useCallback(() => client.disconnect(), [client])
  const switchChain = useCallback(
    () =>
      env.besuChainIdHex
        ? client.switchChain(env.besuChainIdHex, env.besuRpcUrl)
        : Promise.resolve<SwitchChainResult>('failed'),
    [client],
  )

  const wrongChain =
    env.besuChainIdHex !== null && state.chainId !== null && state.chainId !== env.besuChainIdHex

  return {
    ...state,
    wrongChain,
    expectedChainId: env.besuChainIdHex,
    connect,
    disconnect,
    switchChain,
  }
}

/** switchChain 결과를 사용자 문구로 옮긴다. UI 에서 재사용하려고 여기 둔다. */
export function useSwitchChainNotice() {
  const [message, setMessage] = useState<string | null>(null)
  const report = useCallback((result: SwitchChainResult) => {
    switch (result) {
      case 'switched':
      case 'added':
        setMessage(null)
        break
      case 'missing-rpc-url':
        setMessage('네트워크 정보가 아직 설정되지 않았습니다. 관리자에게 문의하세요.')
        break
      case 'rejected':
        setMessage('네트워크 전환을 취소했습니다.')
        break
      default:
        setMessage('네트워크를 전환하지 못했습니다.')
    }
  }, [])
  return { message, report }
}
