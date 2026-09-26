// apiClient 인터셉터의 계약을 고정한다.
//
// 이 파일이 없으면 인터셉터를 고치다가 셋 중 하나를 조용히 깨뜨릴 수 있다 —
// 토큰 주입, 봉투 벗기기, 401 처리. adapter 를 목으로 바꿔 실제 네트워크 없이
// axios 의 요청/응답 인터셉터 체인 전체를 통과시켜 검증한다.

import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from './apiClient.ts'
import { useAuthStore, type AuthUser } from '../stores/authStore.ts'
import { createAppError, ERROR_KIND } from '../utils/errors.ts'

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
  it('업무 실패의 data를 details로 보존하고 검증 오류와 구분한다', async () => {
    const details = [{ productModelId: 1, requested: 3, available: 1 }]
    await expect(apiClient.get('/checkout', { adapter: async config => { throw httpError(config, 409, { ...errorEnvelope('PRODUCT_ORDER_ITEMS_OUT_OF_STOCK', '재고 부족'), data: details }) } })).rejects.toMatchObject({ details, validationErrors: [] })
  })
  beforeEach(() => {
    useAuthStore.setState({
      user: null,
      accessToken: null,
      isAuthenticated: false,
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('토큰이 있으면 Authorization 헤더를 붙인다', async () => {
    useAuthStore.setState({
      accessToken: 'access-token',
      isAuthenticated: true,
      user: FIXTURE_USER,
    })
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

  it('HttpOnly Refresh Token 쿠키를 주고받도록 credentials를 포함한다', () => {
    expect(apiClient.defaults.withCredentials).toBe(true)
  })

  it('401 을 받으면 토큰을 재발급하고 원래 요청을 한 번 재시도한다', async () => {
    useAuthStore.setState({
      accessToken: 'old-access',
      isAuthenticated: true,
      user: FIXTURE_USER,
    })
    const refresh = vi.spyOn(apiClient, 'post').mockResolvedValue({
      data: { accessToken: 'new-access' },
    } as AxiosResponse)
    let attempts = 0
    const authorizationHeaders: Array<string | undefined> = []

    const response = await apiClient.get('/materials', {
      adapter: async (config) => {
        attempts += 1
        authorizationHeaders.push(config.headers.get('Authorization') as string | undefined)
        if (attempts === 1) {
          return Promise.reject(
            httpError(config, 401, errorEnvelope('TOKEN_EXPIRED', '토큰이 만료되었습니다.')),
          )
        }
        return okResponse(config, envelope([{ id: 1 }]))
      },
    })

    expect(refresh).toHaveBeenCalledWith(
      '/v1/auth/refresh',
      undefined,
      { skipAuth: true, skipTokenRefresh: true },
    )
    expect(attempts).toBe(2)
    expect(authorizationHeaders).toEqual(['Bearer old-access', 'Bearer new-access'])
    expect(response.data).toEqual([{ id: 1 }])
    expect(useAuthStore.getState()).toMatchObject({
      accessToken: 'new-access',
      isAuthenticated: true,
    })
  })

  it('여러 요청이 동시에 401 을 받아도 재발급 요청은 하나만 공유한다', async () => {
    useAuthStore.setState({
      accessToken: 'old-access',
      isAuthenticated: true,
      user: FIXTURE_USER,
    })
    let releaseRefresh: ((response: AxiosResponse) => void) | undefined
    const refresh = vi.spyOn(apiClient, 'post').mockReturnValue(
      new Promise((resolve) => {
        releaseRefresh = resolve
      }),
    )

    function expiringRequest(path: string) {
      let attempts = 0
      return apiClient.get(path, {
        adapter: async (config) => {
          attempts += 1
          return attempts === 1
            ? Promise.reject(
                httpError(config, 401, errorEnvelope('TOKEN_EXPIRED', '만료되었습니다.')),
              )
            : okResponse(config, envelope(path))
        },
      })
    }

    const first = expiringRequest('/materials')
    const second = expiringRequest('/trades')
    await vi.waitFor(() => expect(refresh).toHaveBeenCalledTimes(1))
    releaseRefresh!({
      data: { accessToken: 'new-access' },
    } as AxiosResponse)

    const responses = await Promise.all([first, second])
    expect(responses.map((response) => response.data)).toEqual(['/materials', '/trades'])
    expect(refresh).toHaveBeenCalledTimes(1)
  })

  it('재발급이 실패하면 Access Token을 지우고 로그아웃한다', async () => {
    useAuthStore.setState({
      accessToken: 'old-access',
      isAuthenticated: true,
      user: FIXTURE_USER,
    })
    vi.spyOn(apiClient, 'post').mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, {
        code: 'REFRESH_TOKEN_EXPIRED',
        status: 401,
        message: 'Refresh Token이 만료되었습니다.',
      }),
    )

    await expect(
      apiClient.get('/materials', {
        adapter: async (config) =>
          Promise.reject(
            httpError(config, 401, errorEnvelope('TOKEN_EXPIRED', '토큰이 만료되었습니다.')),
          ),
      }),
    ).rejects.toMatchObject({ code: 'REFRESH_TOKEN_EXPIRED' })

    expect(useAuthStore.getState()).toMatchObject({
      user: null,
      accessToken: null,
      isAuthenticated: false,
    })
  })

  it('재발급 뒤 재시도한 요청도 401이면 반복하지 않고 로그아웃한다', async () => {
    useAuthStore.setState({
      accessToken: 'old-access',
      isAuthenticated: true,
      user: FIXTURE_USER,
    })
    const refresh = vi.spyOn(apiClient, 'post').mockResolvedValue({
      data: { accessToken: 'new-access' },
    } as AxiosResponse)
    let attempts = 0

    await expect(
      apiClient.get('/materials', {
        adapter: async (config) => {
          attempts += 1
          return Promise.reject(
            httpError(config, 401, errorEnvelope('TOKEN_EXPIRED', '토큰이 만료되었습니다.')),
          )
        },
      }),
    ).rejects.toMatchObject({ status: 401 })

    expect(refresh).toHaveBeenCalledTimes(1)
    expect(attempts).toBe(2)
    expect(useAuthStore.getState()).toMatchObject({
      user: null,
      accessToken: null,
      isAuthenticated: false,
    })
  })

  it('재발급 성공 후 원래 요청이 다른 오류로 실패해도 로그인 상태를 유지한다', async () => {
    useAuthStore.setState({
      accessToken: 'old-access',
      isAuthenticated: true,
      user: FIXTURE_USER,
    })
    vi.spyOn(apiClient, 'post').mockResolvedValue({
      data: { accessToken: 'new-access' },
    } as AxiosResponse)
    let attempts = 0

    await expect(
      apiClient.get('/processing-jobs', {
        adapter: async (config) => {
          attempts += 1
          return Promise.reject(
            attempts === 1
              ? httpError(config, 401, errorEnvelope('TOKEN_EXPIRED', '토큰이 만료되었습니다.'))
              : httpError(config, 500, errorEnvelope('INTERNAL_SERVER_ERROR', '서버 오류입니다.')),
          )
        },
      }),
    ).rejects.toMatchObject({ status: 500 })

    expect(attempts).toBe(2)
    expect(useAuthStore.getState()).toMatchObject({
      user: FIXTURE_USER,
      accessToken: 'new-access',
      isAuthenticated: true,
    })
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
