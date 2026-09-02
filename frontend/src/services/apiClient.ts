// 앱 전체가 공유하는 단 하나의 Axios 인스턴스.
//
// Component 는 axios 를 직접 부르지 않는다. 호출 경로는 항상 다음과 같다.
//   Page → Feature Component → Custom Hook → TanStack Query → Feature API → apiClient
//
// 이 파일이 지는 책임은 네 가지다.
//   1) Base URL 을 env 한 곳에서만 읽는다 (Component 에 URL 하드코딩 금지)
//   2) Authorization 헤더를 자동으로 붙인다
//   3) Backend 의 ApiResponse 봉투를 벗겨 알맹이만 돌려준다
//   4) 모든 실패를 AppError 로 정규화한다 (401 처리 포함)

import axios, { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { env } from '../config/env.ts'
import { getAccessToken, useAuthStore } from '../stores/authStore.ts'
import type { ApiEnvelope, ValidationError } from '../types/api.types.ts'
import { createAppError, ERROR_KIND, type AppError } from '../utils/errors.ts'

export const apiClient = axios.create({
  baseURL: env.apiBaseUrl,
  timeout: 10_000,
  headers: { 'Content-Type': 'application/json' },
})

// ── Request: 토큰 주입 ────────────────────────────────────
apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getAccessToken()
  if (token) config.headers.set('Authorization', `Bearer ${token}`)
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
  (error: unknown) => Promise.reject(normalizeAxiosError(error)),
)

/** axios 가 던진 무엇이든 AppError 로 바꾼다. */
function normalizeAxiosError(error: unknown): AppError {
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

  if (status === 401) {
    handleUnauthorized()
  }

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
 * 401 공통 처리: 세션을 비운다.
 *
 * 여기서 곧바로 `/login` 으로 navigate 하지 않는 이유 —
 * 이 파일은 React 트리 밖이라 router 에 접근할 수 없고, 억지로 `location.href` 를
 * 쓰면 진행 중이던 입력이 날아간다. 대신 상태만 비우면 ProtectedRoute 가
 * 다음 렌더에서 `/login` 으로 보내고, 사용자가 있던 위치도 함께 넘겨준다.
 */
function handleUnauthorized(): void {
  if (useAuthStore.getState().isAuthenticated) {
    useAuthStore.getState().signOut()
  }
}
