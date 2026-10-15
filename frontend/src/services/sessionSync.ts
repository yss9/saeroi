/**
 * 창 사이의 세션 동기화.
 *
 * <h2>왜 필요한가</h2>
 *
 * AI 어시스턴트가 별도 창으로 열리면 같은 오리진의 창이 둘이 된다. 이 프로젝트의 Refresh
 * Token 은 <b>한 번 쓰면 회전하고 기존 것을 무효화한다</b>
 * (`RefreshTokenService`: `revokeIfActive(...) != 1` 이면 거절).
 *
 * Access Token 수명이 30분이라 두 창이 거의 동시에 만료를 맞는다. 둘 다 재발급을 시도하면
 * 하나가 실패하고, 실패한 쪽은 세션을 비운다. 세션은 `localStorage` 공유라
 * <b>두 창 모두 로그아웃된다.</b>
 *
 * `apiClient` 의 동시 재발급 단일화는 <b>한 창 안에서만</b> 동작한다. 창 사이는 이 파일이 맡는다.
 *
 * <h2>어떻게 막는가</h2>
 *
 * 재발급을 시작하는 창이 채널에 알리고, 끝나면 새 토큰을 알린다. 다른 창은 그동안 기다렸다가
 * 새 토큰을 받아 쓴다. `BroadcastChannel` 이 없는 환경에서는 조용히 비활성화된다 — 그 경우
 * 예전과 같은 동작(각자 재발급)이 되지만 기능이 막히지는 않는다.
 */

const CHANNEL_NAME = 'saeroi.session'

type SessionEvent =
  | { type: 'refresh-started' }
  | { type: 'refresh-succeeded'; accessToken: string; userId: number }
  | { type: 'refresh-failed' }
  | { type: 'signed-out' }

type Listener = (event: SessionEvent) => void

const channel = createChannel()
const listeners = new Set<Listener>()

channel?.addEventListener('message', (event) => {
  const payload = event.data as SessionEvent
  listeners.forEach((listener) => listener(payload))
})

function createChannel(): BroadcastChannel | null {
  // 사파리 구버전과 일부 임베디드 브라우저에 없다. 없으면 기능을 끄되 앱은 돌아야 한다.
  if (typeof BroadcastChannel === 'undefined') return null
  try {
    return new BroadcastChannel(CHANNEL_NAME)
  } catch {
    return null
  }
}

export const sessionSync = {
  /** 이 창이 재발급을 시작했음을 다른 창에 알린다. */
  announceRefreshStarted(): void {
    channel?.postMessage({ type: 'refresh-started' } satisfies SessionEvent)
  },

  announceRefreshSucceeded(accessToken: string, userId: number): void {
    channel?.postMessage({
      type: 'refresh-succeeded',
      accessToken,
      userId,
    } satisfies SessionEvent)
  },

  announceRefreshFailed(): void {
    channel?.postMessage({ type: 'refresh-failed' } satisfies SessionEvent)
  },

  announceSignedOut(): void {
    channel?.postMessage({ type: 'signed-out' } satisfies SessionEvent)
  },

  subscribe(listener: Listener): () => void {
    listeners.add(listener)
    return () => listeners.delete(listener)
  },

  /**
   * 다른 창이 재발급을 끝낼 때까지 기다린다.
   *
   * 무한정 기다리지 않는다. 알림을 보낸 창이 그 사이 닫히면 영영 오지 않으므로, 제한 시간이
   * 지나면 직접 재발급하도록 `false` 를 돌려준다. 그 경우 회전 경합이 날 수 있지만
   * "영원히 멈춤" 보다는 낫다.
   */
  waitForRefresh(timeoutMs = 5_000): Promise<boolean> {
    if (!channel) return Promise.resolve(false)

    return new Promise((resolve) => {
      const timer = setTimeout(() => {
        unsubscribe()
        resolve(false)
      }, timeoutMs)

      const unsubscribe = sessionSync.subscribe((event) => {
        if (event.type === 'refresh-succeeded') {
          clearTimeout(timer)
          unsubscribe()
          resolve(true)
        }
        if (event.type === 'refresh-failed' || event.type === 'signed-out') {
          clearTimeout(timer)
          unsubscribe()
          resolve(false)
        }
      })
    })
  },
}
