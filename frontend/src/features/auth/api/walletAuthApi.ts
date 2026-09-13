// 지갑 서명 로그인의 HTTP 호출.
//
// authApi.ts 와 같은 규칙을 따른다: apiClient 만 쓰고, React 를 모르고,
// 응답 봉투는 인터셉터가 이미 벗겼으므로 알맹이 타입만 다룬다.
//
// 이메일 로그인과 같은 `/v1/auth` 계약을 사용한다.
// apiClient.baseURL 이 '/api' 이므로 최종 URL 은 /api/v1/auth/wallet/* 이다.
//   walletAuthApi.test.ts 가 이 경로를 문자열로 고정해 둔다.

import { apiClient } from '../../../services/apiClient.ts'
import type {
  WalletNonceRequest,
  WalletNonceResponse,
  WalletVerifyRequest,
  WalletVerifyResponse,
} from '../types/auth.types.ts'

/** 1회용 nonce 와 서명할 정본 메시지를 받아온다. 미등록 지갑이면 404 WALLET_NOT_LINKED. */
export async function requestWalletNonce(
  payload: WalletNonceRequest,
): Promise<WalletNonceResponse> {
  const { data } = await apiClient.post<WalletNonceResponse>('/v1/auth/wallet/nonce', payload, {
    skipAuth: true,
    skipTokenRefresh: true,
  })
  return data
}

/** 서명을 보내 검증받고 JWT 를 받는다. */
export async function verifyWalletSignature(
  payload: WalletVerifyRequest,
): Promise<WalletVerifyResponse> {
  const { data } = await apiClient.post<WalletVerifyResponse>('/v1/auth/wallet/verify', payload, {
    skipAuth: true,
    skipTokenRefresh: true,
  })
  return data
}
