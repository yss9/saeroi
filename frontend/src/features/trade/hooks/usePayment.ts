// 소재 거래 결제 훅.
//
// 결제는 "요청 하나 보내고 끝" 이 아니라 **사이트를 떠났다 돌아오는 흐름** 이다.
//
//   ready → 결제창으로 이동(사이트를 떠남) → 인증 → 복귀 → approve → 완료
//
// 서버의 TradeStatus 로는 이 구간을 표현할 수 없다. 결제창에 나가 있는 동안에도 서버는
// 여전히 CREATED 이기 때문이다. 그래서 화면 전용 상태(PaymentPhase)를 따로 둔다.
//
// **떠나는 순간 이 훅의 상태는 사라진다.** 그래서 복귀 후 승인은 이 훅이 아니라
// 복귀 화면(PaymentReturnPage)이 담당한다. 여기 남는 것은 "떠나기 전" 과
// "Mock 이라 떠나지 않은 경우" 다.

import { useCallback, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { toErrorMessage, type AppError } from '../../../utils/errors.ts'
import { marketplaceKeys } from '../../marketplace/api/marketplaceKeys.ts'
import { approvePayment, cancelTrade, readyPayment } from '../api/paymentApi.ts'
import { tradeKeys } from '../api/tradeKeys.ts'
import type { PaymentPhase, Trade } from '../types/trade.types.ts'

export interface UsePaymentResult {
  phase: PaymentPhase
  /** 실패·취소 시 사용자에게 보여줄 문구 */
  message: string | null
  /** PREPARING · AWAITING_PG · CONFIRMING — 버튼을 잠글 때 쓴다 */
  isBusy: boolean
  /**
   * 실제 PG 가 붙어 있는가. 준비를 마친 뒤에만 값이 있다.
   *
   * false 면 결제창이 열리지 않는다. 화면이 그 사실을 사용자에게 알려야 한다 —
   * 없으면 "버튼을 눌렀는데 아무 일도 안 난다" 로 읽힌다.
   */
  live: boolean | null
  /** 결제 시작. 실제 PG 면 결제창으로 이동한다(이 페이지를 떠난다) */
  start: (tradeId: number) => Promise<void>
  /** Mock 경로에서만 쓴다. 실제 PG 는 복귀 화면이 승인한다 */
  approve: (tradeId: number) => Promise<void>
  /** 거래 취소 (결제 전/후 모두) */
  cancel: (tradeId: number) => Promise<void>
  reset: () => void
}

/** 결제창에서 돌아왔을 때 어느 거래였는지 복원하려고 남긴다. */
export const PAYMENT_TRADE_KEY = 'payment.tradeId'

export function usePayment(): UsePaymentResult {
  const queryClient = useQueryClient()
  const [phase, setPhase] = useState<PaymentPhase>('IDLE')
  const [message, setMessage] = useState<string | null>(null)
  const [live, setLive] = useState<boolean | null>(null)

  // 중복 실행 방지. 결제 버튼을 두 번 누르면 결제가 두 번 나갈 수 있고 되돌리기 어렵다.
  // state 로 막으면 반영 전 두 번째 클릭을 놓친다.
  const running = useRef(false)

  const reset = useCallback(() => {
    running.current = false
    setPhase('IDLE')
    setMessage(null)
    setLive(null)
  }, [])

  const refreshTrade = useCallback(
    (trade: Trade) => {
      queryClient.setQueryData(tradeKeys.detail(trade.tradeId), trade)
      void queryClient.invalidateQueries({ queryKey: tradeKeys.lists() })
      // 취소는 판매 가능 수량을 되돌린다. 목록이 그대로면 사용자는 되살아난 수량을
      // 못 보거나, 반대로 없는 수량을 보고 있게 된다.
      void queryClient.invalidateQueries({ queryKey: marketplaceKeys.all })
    },
    [queryClient],
  )

  /**
   * 결제 준비 후 결제창으로 보낸다.
   *
   * `redirectUrl` 이 있으면 **이 페이지를 떠난다.** 떠나기 전에 거래 id 를
   * sessionStorage 에 남기는 이유 — 복귀 URL 이 거래 id 를 경로에 담고 있지만,
   * 카카오가 그 주소로 돌려보내지 못하는 경우(창을 직접 닫는 등)에도 화면이 어느 거래를
   * 정리해야 하는지 알아야 한다.
   */
  const start = useCallback(async (tradeId: number) => {
    if (running.current) return
    running.current = true
    setPhase('PREPARING')
    setMessage(null)

    try {
      const ready = await readyPayment(tradeId)
      setLive(ready.live)
      setPhase('AWAITING_PG')

      if (ready.redirectUrl) {
        try {
          sessionStorage.setItem(PAYMENT_TRADE_KEY, String(tradeId))
        } catch {
          // 사파리 프라이빗 모드 등에서 막힌다. 복귀 URL 에 거래 id 가 있으므로
          // 저장 실패가 결제를 막을 이유는 없다.
        }
        window.location.href = ready.redirectUrl
      }
      // redirectUrl 이 없으면 Mock 이다. AWAITING_PG 에 머물고 화면이 승인 버튼을 띄운다.
    } catch (error) {
      setPhase('FAILED')
      setMessage(toErrorMessage(error as AppError))
    } finally {
      running.current = false
    }
  }, [])

  /**
   * Mock 경로 승인.
   *
   * 실제 PG 에서는 이 함수가 쓰이지 않는다 — 브라우저가 카카오페이로 떠났다가 복귀 URL 로
   * 돌아오고, 그 화면이 `pg_token` 을 읽어 승인한다. 여기서 보내는 토큰은 Mock 이
   * 검사하지 않는 자리값이다.
   */
  const approve = useCallback(
    async (tradeId: number) => {
      if (running.current) return
      running.current = true
      setPhase('CONFIRMING')

      try {
        const trade = await approvePayment(tradeId, 'MOCK-PG-TOKEN')
        refreshTrade(trade)
        setPhase('SUCCEEDED')
        setMessage(null)
      } catch (error) {
        setPhase('FAILED')
        setMessage(toErrorMessage(error as AppError))
      } finally {
        running.current = false
      }
    },
    [refreshTrade],
  )

  const cancel = useCallback(
    async (tradeId: number) => {
      if (running.current) return
      running.current = true

      try {
        const trade = await cancelTrade(tradeId)
        refreshTrade(trade)
        setPhase('CANCELED')
        setMessage(null)
      } catch (error) {
        // 취소 실패는 결제 실패와 다르다. 이전 단계로 되돌리지 않고 사유만 알린다.
        setMessage(toErrorMessage(error as AppError))
      } finally {
        running.current = false
      }
    },
    [refreshTrade],
  )

  return {
    phase,
    message,
    isBusy: phase === 'PREPARING' || phase === 'AWAITING_PG' || phase === 'CONFIRMING',
    live,
    start,
    approve,
    cancel,
    reset,
  }
}
