import type { UserRole, UserStatus } from '../../../types/role.types.ts'

/** Backend `users` 테이블에 대응하는 조회 모델. DTO 필드명은 camelCase 기준. */
export interface UserProfile {
  id: number
  email: string
  name: string
  phone: string
  role: UserRole
  status: UserStatus
  createdAt: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface AccessTokenResponse {
  accessToken: string
}

export interface LoginResponse extends AccessTokenResponse {
  user: UserProfile
}

// ── 지갑 서명 로그인 ────────────────────────────────────────
// Backend 와 확정된 계약. 바꾸려면 양쪽을 같이 바꾼다.

/** POST /v1/auth/wallet/nonce 요청. */
export interface WalletNonceRequest {
  /** 지갑이 보고한 EIP-55 체크섬 주소. 서버가 소문자로 정규화해 저장한다. */
  walletAddress: string
  /**
   * 10진수 체인 ID. 지갑은 '0x539' 로 주지만 Backend `wallet_nonce.chain_id` 가
   * BIGINT 라 숫자로 보낸다. 등록된 체인과 다르면 서버가 WALLET_CHAIN_MISMATCH 로 거부한다.
   */
  chainId: number
}

export interface WalletNonceResponse {
  /** 서버가 계산한 체크섬 주소. 요청이 보낸 대소문자가 아니다. */
  walletAddress: string
  /** 32자 hex. 1회용. */
  nonce: string
  /**
   * 서명할 문자열의 **정본**. 서버가 검증할 때 재구성하는 것과 같은 값이다.
   * 프론트는 buildLoginMessage() 결과와 대조한 뒤 이 값에 서명한다 —
   * 양쪽 구현이 어긋나면 서명이 만들어지기 전에 드러난다.
   */
  message: string
  expiresAt: string
}

/** POST /v1/auth/wallet/verify 요청. nonce 를 함께 보내 어느 발급분인지 특정한다. */
export interface WalletVerifyRequest {
  walletAddress: string
  nonce: string
  /** 0x + 130자 hex (r|s|v). */
  signature: string
}

/** 이메일 로그인과 같은 모양이라 세션을 세우는 경로가 하나로 유지된다. */
export type WalletVerifyResponse = LoginResponse
