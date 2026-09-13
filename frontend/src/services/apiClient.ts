// 앱 전체가 공유하는 단 하나의 Axios 인스턴스.
//
// Component 는 axios 를 직접 부르지 않는다. 호출 경로는 항상 다음과 같다.
//   Page → Feature Component → Custom Hook → TanStack Query → Feature API → apiClient
//
// 이 파일이 지는 책임은 네 가지다.
//   1) Base URL 을 env 한 곳에서만 읽는다 (Component 에 URL 하드코딩 금지)
//   2) Authorization 헤더를 자동으로 붙인다
//   3) Backend 의 ApiResponse 봉투를 벗겨 알맹이만 돌려준다
//   4) 401이면 토큰을 한 번 재발급하고 원래 요청을 재시도한다
//   5) 모든 실패를 AppError 로 정규화한다

import axios, { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { env } from '../config/env.ts'
import { getAccessToken, useAuthStore } from '../stores/authStore.ts'
import type { ApiEnvelope, ValidationError } from '../types/api.types.ts'
import { createAppError, ERROR_KIND, isAppError, type AppError } from '../utils/errors.ts'

declare module 'axios' {
  interface AxiosRequestConfig {
    /** Access Token을 붙이면 안 되는 인증 요청에서만 사용한다. */
    skipAuth?: boolean
    /** 이 요청의 401은 Refresh Token 재발급 대상으로 삼지 않는다. */
    skipTokenRefresh?: boolean
    /** 재발급 뒤 같은 요청을 두 번 이상 반복하지 않기 위한 내부 표시다. */
    retriedAfterTokenRefresh?: boolean
    /** 요청을 보낸 계정과 현재 계정이 같은지 판별하기 위한 내부 값이다. */
    sessionUserId?: number
  }
}

interface RefreshedAccessToken {
  accessToken: string
}

const REFRESH_PATH = '/v1/auth/refresh'
let refreshInFlight: Promise<void> | null = null

export const apiClient = axios.create({
  baseURL: env.apiBaseUrl,
  // Refresh Token은 HttpOnly 쿠키이므로 교차 Origin 개발 환경에서도 쿠키를 주고받는다.
  withCredentials: true,
  timeout: 10_000,
  headers: { 'Content-Type': 'application/json' },
})

// ── Request: 토큰 주입 ────────────────────────────────────
apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  if (config.skipAuth) {
    config.headers.delete('Authorization')
    return config
  }

  const token = getAccessToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
    config.sessionUserId = useAuthStore.getState().user?.id
  }
  return config
})

// ── Response: 봉투 제거 + 에러 정규화 ──────────────────────
//
// Backend 는 성공/실패 모두 { success, code, message, data, timestamp } 로 감싼다.
// 매 호출부에서 `res.data.data` 를 쓰는 건 실수하기 쉬우므로 여기서 한 번만 벗긴다.
// 그 결과 Feature API 함수의 반환 타입이 곧 실제 도메인 타입이 된다.
apiClient.interceptors.response.use(
  (response: AxiosResponse<ApiEnvelope<unknown>>) => {
    const envelope = response.data
    // 봉투 형태가 아니면(파일 다운로드 등) 손대지 않고 그대로 통과시킨다.
    if (envelope && typeof envelope === 'object' && 'success' in envelope) {
      // 봉투를 벗기면 런타임 형태가 바뀐다. 호출부가 보는 타입은 벗겨진 쪽이므로
      // 여기서만 단언한다 — 이 한 줄이 앱 전체에서 `res.data.data` 를 없애준다.
      ;(response as AxiosResponse<unknown>).data = envelope.data
    }
    return response
  },
  handleResponseError,
)

async function handleResponseError(error: unknown): Promise<AxiosResponse> {
  if (!axios.isAxiosError(error) || error.response?.status !== 401) {
    return Promise.reject(normalizeAxiosError(error))
  }

  const config = error.config
  const session = useAuthStore.getState()

  if (
    !config ||
    config.skipTokenRefresh ||
    config.retriedAfterTokenRefresh ||
    !session.user ||
    !session.accessToken ||
    !session.isAuthenticated
  ) {
    handleUnauthorized()
    return Promise.reject(normalizeAxiosError(error))
  }

  // 요청 이후 다른 계정으로 로그인했다면 그 세션을 건드리거나 요청을 대신 재실행하지 않는다.
  if (config.sessionUserId !== useAuthStore.getState().user?.id) {
    return Promise.reject(normalizeAxiosError(error))
  }

  config.retriedAfterTokenRefresh = true

  try {
    // 먼저 끝난 동시 요청이 이미 토큰을 바꿨다면 다시 회전시키지 않고 새 토큰으로 재시도한다.
    if (requestUsedCurrentAccessToken(config)) {
      await getOrStartTokenRefresh()
    }
    return await apiClient.request(config)
  } catch (refreshError: unknown) {
    handleUnauthorized()
    return Promise.reject(normalizeAxiosError(refreshError))
  }
}

function requestUsedCurrentAccessToken(config: InternalAxiosRequestConfig): boolean {
  const currentAccessToken = getAccessToken()
  if (!currentAccessToken) return true
  return config.headers.get('Authorization') === `Bearer ${currentAccessToken}`
}

function getOrStartTokenRefresh(): Promise<void> {
  if (refreshInFlight) return refreshInFlight

  refreshInFlight = refreshSession().then(
    () => {
      refreshInFlight = null
    },
    (error: unknown) => {
      refreshInFlight = null
      return Promise.reject(error)
    },
  )
  return refreshInFlight
}

async function refreshSession(): Promise<void> {
  const session = useAuthStore.getState()
  if (!session.user || !session.accessToken || !session.isAuthenticated) {
    throw createAppError(ERROR_KIND.API_ERROR, {
      code: 'INVALID_REFRESH_TOKEN',
      status: 401,
      message: '로그인이 만료되었습니다. 다시 로그인해 주세요.',
    })
  }

  const { data } = await apiClient.post<RefreshedAccessToken>(
    REFRESH_PATH,
    undefined,
    { skipAuth: true, skipTokenRefresh: true },
  )

  if (!isRefreshedAccessToken(data)) {
    throw createAppError(ERROR_KIND.UNKNOWN, {
      message: '토큰 재발급 응답 형식이 올바르지 않습니다.',
    })
  }

  const refreshed = useAuthStore
    .getState()
    .refreshAccessToken(session.user.id, session.accessToken, data.accessToken)
  if (!refreshed) {
    throw createAppError(ERROR_KIND.UNKNOWN, {
      message: '로그인 상태가 변경되어 토큰을 갱신하지 않았습니다.',
    })
  }
}

function isRefreshedAccessToken(value: unknown): value is RefreshedAccessToken {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<RefreshedAccessToken>
  return typeof candidate.accessToken === 'string' && candidate.accessToken.length > 0
}

/** axios 가 던진 무엇이든 AppError 로 바꾼다. */
function normalizeAxiosError(error: unknown): AppError {
  if (isAppError(error)) return error

  if (!axios.isAxiosError(error)) {
    return createAppError(ERROR_KIND.UNKNOWN, { cause: error })
  }

  const axiosError = error as AxiosError<ApiEnvelope<ValidationError[] | null>>
  const response = axiosError.response

  // 응답 자체가 없다 → 네트워크/타임아웃/CORS
  if (!response) {
    return createAppError(ERROR_KIND.NETWORK_ERROR, { cause: error })
  }

  const envelope = response.data
  const status = response.status

  return createAppError(ERROR_KIND.API_ERROR, {
    // Backend 가 사용자용 문구를 내려주므로 그대로 쓴다.
    message: envelope?.message,
    code: envelope?.code ?? null,
    status,
    validationErrors: Array.isArray(envelope?.data) ? envelope.data : [],
    cause: error,
  })
}

/**
 * Refresh Token 재발급까지 실패한 401 공통 처리: 세션을 비운다.
 *
 * 여기서 곧바로 `/login` 으로 navigate 하지 않는 이유 —
 * 이 파일은 React 트리 밖이라 router 에 접근할 수 없고, 억지로 `location.href` 를
 * 쓰면 진행 중이던 입력이 날아간다. 대신 상태만 비우면 ProtectedRoute 가
 * 다음 렌더에서 `/login` 으로 보내고, 사용자가 있던 위치도 함께 넘겨준다.
 */
function handleUnauthorized(): void {
  const state = useAuthStore.getState()
  if (state.user || state.accessToken || state.isAuthenticated) state.signOut()
}
