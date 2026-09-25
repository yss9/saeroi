// walletClient.ts 의 클라이언트를 React 에 붙이는 얇은 계층.
// 지갑 로직은 여기 두지 않는다 — 전부 walletClient.ts / walletService.ts 에 있고
// 여기서는 배선만 한다.

import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react'
import { env } from '../../../config/env.ts'
import { useWalletStore } from '../../../stores/walletStore.ts'
import { createWalletClient, type SwitchChainResult } from '../services/walletClient.ts'
import { getInjectedProvider } from '../services/walletService.ts'
import type { WalletState } from '../types/wallet.types.ts'

export interface UseWalletResult extends WalletState {
  /** 연결된 체인이 기대값과 다른가. 기대값(VITE_BESU_CHAIN_ID)이 없으면 항상 false. */
  wrongChain: boolean
  /** 기대 체인 ID (16진수). 미설정이면 null. */
  expectedChainId: string | null
  connect: () => void
  selectAccount: () => void
  disconnect: () => void
  switchChain: () => Promise<SwitchChainResult>
}

export function useWallet(): UseWalletResult {
  const client = useMemo(() => createWalletClient(getInjectedProvider()), [])

  // 지갑 이벤트 리스너의 부착/해제는 client.subscribe 가 알아서 한다 —
  // 아래 useSyncExternalStore 가 구독하는 동안만 붙어 있고, 언마운트로 구독이
  // 풀리면 자동으로 떨어진다. 별도의 정리용 useEffect 를 두면 StrictMode 의
  // 마운트→언마운트→재마운트에서 리스너가 떨어진 채 복구되지 않는다.

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

  // 지갑 상태를 전역 스토어로 흘려보낸다.
  //
  // 이 훅을 쓰지 않는 먼 컴포넌트(헤더 배지, 트랜잭션 훅 등)도 주소를 알아야 하기
  // 때문이다. 스토어에는 직렬화 가능한 값만 넣는다 — Provider/Signer 는 넣지 않는다.
  // 여기가 walletClient → Zustand 로 가는 **단방향** 통로이며, 반대 방향은 없다.
  const setWallet = useWalletStore((store) => store.setWallet)
  useEffect(() => {
    setWallet({ address: state.address, chainId: state.chainId })
  }, [state.address, state.chainId, setWallet])

  const connect = useCallback(() => void client.connect(), [client])
  const selectAccount = useCallback(() => void client.selectAccount(), [client])
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
    selectAccount,
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
        setMessage('지갑 연결 환경이 아직 설정되지 않았습니다. 관리자에게 문의하세요.')
        break
      case 'rejected':
        setMessage('연결 환경 변경을 취소했습니다.')
        break
      default:
        setMessage('연결 환경을 변경하지 못했습니다.')
    }
  }, [])
  return { message, report }
}
