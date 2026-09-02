// 지갑 전역 상태.
//
// ⚠️ 여기에는 **직렬화 가능한 값만** 넣는다.
// BrowserProvider / JsonRpcSigner / Contract 인스턴스는 절대 저장하지 않는다.
// 그 객체들은 살아 있는 연결을 들고 있어서 스토어에 넣으면
//   1) persist 직렬화가 깨지고
//   2) 계정·체인 전환 후에도 낡은 객체를 계속 들고 있게 된다.
// 필요할 때마다 walletService.getSigner() 로 새로 얻어 쓴다.

import { create } from 'zustand'

interface WalletState {
  /** EIP-55 체크섬 정규화된 주소 */
  address: string | null
  /** 16진수 문자열 (예: '0x539') */
  chainId: string | null
  isConnected: boolean

  setWallet: (patch: { address: string | null; chainId: string | null }) => void
  clear: () => void
}

export const useWalletStore = create<WalletState>((set) => ({
  address: null,
  chainId: null,
  isConnected: false,

  setWallet: ({ address, chainId }) =>
    set({ address, chainId, isConnected: address !== null }),

  clear: () => set({ address: null, chainId: null, isConnected: false }),
}))
