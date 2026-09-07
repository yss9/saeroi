// 소재 거래 결제 훅.
//
// 결제는 "요청 하나 보내고 끝" 이 아니라 **여러 단계를 오가는 흐름** 이다.
//
//   준비 요청 → PG 결제창(사이트를 떠남) → 돌아옴 → 승인 요청 → 완료
//
// 서버의 TradeStatus 로는 이 구간을 표현할 수 없다. 결제창에 나가 있는 동안에도
// 서버는 여전히 CREATED 이기 때문이다. 그래서 화면 전용 상태(PaymentPhase)를 따로 둔다.
//
// useBlockchainTransaction 과 같은 이유의 설계다 — 외부 시스템을 오가는 동안의
// 중간 상태를 화면이 알아야 한다.

import { useCallback, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { toErrorMessage, type AppError } from '../../../utils/errors.ts'
import { marketplaceKeys } from '../../marketplace/api/marketplaceKeys.ts'
import { cancelTrade, confirmPayment, preparePayment } from '../api/paymentApi.ts'
import { tradeKeys } from '../api/tradeKeys.ts'
import type { PaymentPhase, TradeDetail } from '../types/trade.types.ts'

export interface UsePaymentResult {
  phase: PaymentPhase
  /** 실패·취소 시 사용자에게 보여줄 문구 */
  message: string | null
  /** PREPARING · AWAITING_PG · CONFIRMING — 버튼을 잠글 때 쓴다 */
  isBusy: boolean
  /** 결제 시작. 준비 요청 후 결제창으로 보낸다 */
  start: (tradeId: number) => Promise<void>
  /** 결제창에서 돌아온 뒤 승인 */
  confirm: (tradeId: number, pgToken: string) => Promise<void>
  /** 거래 취소 (결제 전/후 모두) */
  cancel: (tradeId: number, reason?: string) => Promise<void>
  reset: () => void
}

export function usePayment(): UsePaymentResult {
  const queryClient = useQueryClient()
  const [phase, setPhase] = useState<PaymentPhase>('IDLE')
  const [message, setMessage] = useState<string | null>(null)

  // 준비 단계에서 받은 tid 를 승인까지 들고 있어야 한다.
  // 화면에 보일 값이 아니므로 state 가 아니라 ref 다.
  const tidRef = useRef<string | null>(null)

  // 중복 실행 방지. 결제 버튼을 두 번 누르면 결제가 두 번 나갈 수 있고 되돌리기 어렵다.
  // state 로 막으면 반영 전 두 번째 클릭을 놓친다.
  const running = useRef(false)

  const reset = useCallback(() => {
    running.current = false
    tidRef.current = null
    setPhase('IDLE')
    setMessage(null)
  }, [])

  const refreshTrade = useCallback(
    (trade: TradeDetail) => {
      queryClient.setQueryData(tradeKeys.detail(trade.id), trade)
      void queryClient.invalidateQueries({ queryKey: tradeKeys.lists() })
      void queryClient.invalidateQueries({ queryKey: marketplaceKeys.all })
    },
    [queryClient],
  )

  const start = useCallback(async (tradeId: number) => {
    if (running.current) return
    running.current = true
    setPhase('PREPARING')
    setMessage(null)

    try {
      const { tid, redirectUrl } = await preparePayment(tradeId)
      tidRef.current = tid
      setPhase('AWAITING_PG')

      // 실제 연동에서는 여기서 카카오페이 결제창으로 이동한다.
      // 사이트를 떠나므로 돌아왔을 때 어느 거래였는지 복원할 수 있어야 한다.
      //
      // TODO(3번): Backend 가 redirectUrl 을 주면 아래를 살린다.
      //   sessionStorage.setItem('payment.tradeId', String(tradeId))
      //   window.location.href = redirectUrl
      //
      // sessionStorage 를 쓰는 이유 — 페이지를 떠났다 돌아오면 메모리 상태가 사라진다.
      // 결제창 복귀 URL 의 쿼리스트링에 담을 수도 있지만, 그러면 사용자가 주소를
      // 고쳐 다른 거래를 승인시킬 수 있다.
      void redirectUrl
    } catch (error) {
      setPhase('FAILED')
      setMessage(toErrorMessage(error))
    } finally {
      running.current = false
    }
  }, [])

  const confirm = useCallback(
    async (tradeId: number, pgToken: string) => {
      if (running.current) return
      running.current = true
      setPhase('CONFIRMING')

      try {
        const tid = tidRef.current
        if (!tid) {
          throw new Error('결제 정보를 찾을 수 없습니다. 처음부터 다시 시도해 주세요.')
        }
        const trade = await confirmPayment(tradeId, { tid, pgToken })
        refreshTrade(trade)
        setPhase('SUCCEEDED')
        setMessage(null)
      } catch (error) {
        setPhase('FAILED')
        setMessage(toErrorMessage(error))
      } finally {
        running.current = false
      }
    },
    [refreshTrade],
  )

  const cancel = useCallback(
    async (tradeId: number, reason?: string) => {
      if (running.current) return
      running.current = true

      try {
        const trade = await cancelTrade(tradeId, reason)
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
    start,
    confirm,
    cancel,
    reset,
  }
}
