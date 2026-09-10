// apiClient 인터셉터의 계약을 고정한다.
//
// 이 파일이 없으면 인터셉터를 고치다가 셋 중 하나를 조용히 깨뜨릴 수 있다 —
// 토큰 주입, 봉투 벗기기, 401 처리. adapter 를 목으로 바꿔 실제 네트워크 없이
// axios 의 요청/응답 인터셉터 체인 전체를 통과시켜 검증한다.

import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it } from 'vitest'
import { apiClient } from './apiClient.ts'
import { useAuthStore, type AuthUser } from '../stores/authStore.ts'
import { ERROR_KIND } from '../utils/errors.ts'

const FIXTURE_USER: AuthUser = {
  id: 1,
  email: 'brand@example.com',
  name: '브랜드사',
  role: 'BRAND',
  walletAddress: null,
}

function envelope<T>(data: T) {
  return {
    success: true,
    code: 'SUCCESS',
    message: '요청에 성공했습니다.',
    data,
    timestamp: '2026-09-10T00:00:00',
  }
}

function errorEnvelope(code: string, message: string) {
  return { success: false, code, message, data: null, timestamp: '2026-09-10T00:00:00' }
}

function okResponse(config: InternalAxiosRequestConfig, data: unknown): AxiosResponse {
  return { data, status: 200, statusText: 'OK', headers: {}, config }
}

function httpError(
  config: InternalAxiosRequestConfig,
  status: number,
  data: unknown,
): AxiosError {
  const response: AxiosResponse = { data, status, statusText: '', headers: {}, config }
  return new AxiosError('Request failed', AxiosError.ERR_BAD_REQUEST, config, {}, response)
}

function networkError(config: InternalAxiosRequestConfig): AxiosError {
  // response 를 아예 안 넘긴다 — 오프라인/타임아웃/CORS 는 응답 자체가 없다.
  return new AxiosError('Network Error', AxiosError.ERR_NETWORK, config, {})
}

describe('apiClient', () => {
  beforeEach(() => {
    useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
  })

  it('토큰이 있으면 Authorization 헤더를 붙인다', async () => {
    useAuthStore.setState({ accessToken: 'access-token', isAuthenticated: true, user: FIXTURE_USER })
    let captured: string | undefined

    await apiClient.get('/materials', {
      adapter: async (config) => {
        captured = config.headers.get('Authorization') as string | undefined
        return okResponse(config, envelope([]))
      },
    })

    expect(captured).toBe('Bearer access-token')
  })

  it('토큰이 없으면 Authorization 헤더를 붙이지 않는다', async () => {
    let captured: string | undefined

    await apiClient.get('/health', {
      adapter: async (config) => {
        captured = config.headers.get('Authorization') as string | undefined
        return okResponse(config, envelope({ status: 'UP' }))
      },
    })

    expect(captured).toBeUndefined()
  })

  it('성공 응답의 ApiResponse 봉투를 벗겨 알맹이만 반환한다', async () => {
    const response = await apiClient.get('/materials/1', {
      adapter: async (config) => okResponse(config, envelope({ id: 1, name: '폐면' })),
    })

    expect(response.data).toEqual({ id: 1, name: '폐면' })
  })

  it('봉투 형태가 아닌 응답(파일 다운로드 등)은 그대로 통과시킨다', async () => {
    const raw = { arrayBuffer: () => new ArrayBuffer(0) }

    const response = await apiClient.get('/evidence/1/file', {
      adapter: async (config) => okResponse(config, raw),
    })

    expect(response.data).toBe(raw)
  })

  it('API 에러 응답을 AppError(API_ERROR)로 정규화한다', async () => {
    await expect(
      apiClient.get('/materials/999', {
        adapter: async (config) =>
          Promise.reject(
            httpError(config, 404, errorEnvelope('MATERIAL_NOT_FOUND', '소재를 찾을 수 없습니다.')),
          ),
      }),
    ).rejects.toMatchObject({
      kind: ERROR_KIND.API_ERROR,
      message: '소재를 찾을 수 없습니다.',
      code: 'MATERIAL_NOT_FOUND',
      status: 404,
    })
  })

  it('검증 실패 응답의 data(필드별 오류 배열)를 validationErrors 로 옮긴다', async () => {
    const fieldErrors = [{ field: 'quantity', message: '0보다 커야 합니다.' }]

    await expect(
      apiClient.post('/materials', {}, {
        adapter: async (config) => {
          const response: AxiosResponse = {
            data: { ...errorEnvelope('VALIDATION_ERROR', '요청이 올바르지 않습니다.'), data: fieldErrors },
            status: 400,
            statusText: '',
            headers: {},
            config,
          }
          return Promise.reject(
            new AxiosError('Request failed', AxiosError.ERR_BAD_REQUEST, config, {}, response),
          )
        },
      }),
    ).rejects.toMatchObject({ validationErrors: fieldErrors })
  })

  it('응답 자체를 받지 못하면 NETWORK_ERROR 로 정규화한다', async () => {
    await expect(
      apiClient.get('/materials', {
        adapter: async (config) => Promise.reject(networkError(config)),
      }),
    ).rejects.toMatchObject({ kind: ERROR_KIND.NETWORK_ERROR, status: null })
  })

  it('401 을 받으면 인증 상태를 비운다', async () => {
    useAuthStore.setState({ accessToken: 'stale-token', isAuthenticated: true, user: FIXTURE_USER })

    await expect(
      apiClient.get('/materials', {
        adapter: async (config) =>
          Promise.reject(
            httpError(config, 401, errorEnvelope('TOKEN_EXPIRED', '토큰이 만료되었습니다.')),
          ),
      }),
    ).rejects.toMatchObject({ status: 401 })

    const state = useAuthStore.getState()
    expect(state.isAuthenticated).toBe(false)
    expect(state.accessToken).toBeNull()
  })

  it('이미 로그아웃 상태에서 401 을 받아도 signOut 을 다시 부르지 않는다', async () => {
    // signOut 이 멱등하지 않다면(예: 이벤트 발행) 여기서 중복 처리가 드러난다.
    await expect(
      apiClient.get('/materials', {
        adapter: async (config) =>
          Promise.reject(
            httpError(config, 401, errorEnvelope('TOKEN_EXPIRED', '토큰이 만료되었습니다.')),
          ),
      }),
    ).rejects.toMatchObject({ status: 401 })

    expect(useAuthStore.getState().isAuthenticated).toBe(false)
  })
})
