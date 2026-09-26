import type { AxiosResponse } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../../services/apiClient.ts'
import type { LoginResponse } from '../types/auth.types.ts'
import { login, logout } from './authApi.ts'

const REQUEST = {
  email: 'consumer@example.com',
  password: 'Password!1',
}

const RESPONSE: LoginResponse = {
  accessToken: 'jwt-access-token',
  user: {
    id: 1,
    email: REQUEST.email,
    name: '소비자',
    phone: '010-1234-5678',
    role: 'CONSUMER',
    status: 'ACTIVE',
    createdAt: '2026-09-08T12:00:00',
  },
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('login', () => {
  it('Backend 이메일 로그인 경로로 자격 증명을 전송한다', async () => {
    const post = vi
      .spyOn(apiClient, 'post')
      .mockResolvedValue({ data: RESPONSE } as AxiosResponse<LoginResponse>)

    await login(REQUEST)

    expect(post).toHaveBeenCalledWith('/v1/auth/login', REQUEST, {
      skipAuth: true,
      skipTokenRefresh: true,
    })
  })

  it('공통 응답 봉투가 벗겨진 로그인 결과를 그대로 반환한다', async () => {
    vi.spyOn(apiClient, 'post')
      .mockResolvedValue({ data: RESPONSE } as AxiosResponse<LoginResponse>)

    await expect(login(REQUEST)).resolves.toEqual(RESPONSE)
  })
})

describe('logout', () => {
  it('토큰 재발급 없이 쿠키 기반 로그아웃을 요청한다', async () => {
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: null } as AxiosResponse)

    await logout()

    expect(post).toHaveBeenCalledWith('/v1/auth/logout', undefined, {
      skipAuth: true,
      skipTokenRefresh: true,
    })
  })
})
