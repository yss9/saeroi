import { apiClient } from '../../../services/apiClient.ts'
import type { WalletNonceResponse } from '../../auth/types/auth.types.ts'

export interface WalletRegistrationNonceRequest {
  walletAddress: string
  chainId: number
}

export interface WalletRegistrationRequest extends WalletRegistrationNonceRequest {
  nonce: string
  signature: string
}

export interface RegisteredWallet {
  id: number
  walletAddress: string
  chainId: number
  active: boolean
  verified: boolean
  verifiedAt: string | null
  createdAt: string
  deactivatedAt: string | null
}

/** 인증된 사업자 계정에 지갑을 등록하기 위한 1회용 서명 메시지를 발급한다. */
export async function requestWalletRegistrationNonce(
  payload: WalletRegistrationNonceRequest,
): Promise<WalletNonceResponse> {
  const { data } = await apiClient.post<WalletNonceResponse>('/v1/wallets/nonce', payload)
  return data
}

/** 서명을 검증하고 현재 계정의 활성 지갑으로 등록한다. */
export async function registerWallet(
  payload: WalletRegistrationRequest,
): Promise<RegisteredWallet> {
  const { data } = await apiClient.post<RegisteredWallet>('/v1/wallets', payload)
  return data
}
