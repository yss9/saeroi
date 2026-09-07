// 소재 거래 결제 훅.
//
// 결제는 "요청 하나 보내고 끝" 이 아니라 **여러 단계를 오가는 흐름** 이다.
//
//   시작 → PG 결제창(사이트를 떠남) → 돌아옴 → 승인 요청 → 완료
//
// 서버의 TradeStatus 로는 이 구간을 표현할 수 없다. 결제창에 나가 있는 동안에도
// 서버는 여전히 CREATED 이기 때문이다. 그래서 화면 전용 상태(PaymentPhase)를 따로 둔다.
//
// useBlockchainTransaction 과 같은 이유의 설계다 — 외부 시스템을 오가는 동안의
// 중간 상태를 화면이 알아야 한다.
//
// ⚠️ **PG 연동 전이다.** 서버가 카카오페이를 부르지 않으므로 `ready` 단계에 해당하는
// 호출이 없다. `start` 는 결제창으로 나가는 대신 승인 번호를 만들어 두고 화면을
// AWAITING_PG 로 옮긴다. 연동이 붙으면 `start` 안쪽만 바뀌고 이 훅의 인터페이스와
// 화면의 상태 흐름은 그대로다 — 그러라고 단계를 나눠 둔 것이다.

import { useCallback, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { toErrorMessage, type AppError } from '../../../utils/errors.ts'
import { marketplaceKeys } from '../../marketplace/api/marketplaceKeys.ts'
import { cancelTrade, confirmPayment } from '../api/paymentApi.ts'
import { tradeKeys } from '../api/tradeKeys.ts'
import type { PaymentPhase, Trade } from '../types/trade.types.ts'

export interface UsePaymentResult {
  phase: PaymentPhase
  /** 실패·취소 시 사용자에게 보여줄 문구 */
  message: string | null
  /** PREPARING · AWAITING_PG · CONFIRMING — 버튼을 잠글 때 쓴다 */
  isBusy: boolean
  /** 결제 시작. 연동 후에는 결제창으로 보낸다 */
  start: (tradeId: number) => void
  /** 결제창에서 돌아온 뒤 승인. 금액은 거래 금액과 같아야 한다 */
  confirm: (tradeId: number, amount: number) => Promise<void>
  /** 거래 취소 (결제 전/후 모두) */
  cancel: (tradeId: number) => Promise<void>
  reset: () => void
}

export function usePayment(): UsePaymentResult {
  const queryClient = useQueryClient()
  const [phase, setPhase] = useState<PaymentPhase>('IDLE')
  const [message, setMessage] = useState<string | null>(null)

  // 승인 번호를 승인 요청까지 들고 있어야 한다.
  // 화면에 보일 값이 아니므로 state 가 아니라 ref 다.
  const referenceRef = useRef<string | null>(null)

  // 중복 실행 방지. 결제 버튼을 두 번 누르면 결제가 두 번 나갈 수 있고 되돌리기 어렵다.
  // state 로 막으면 반영 전 두 번째 클릭을 놓친다.
  const running = useRef(false)

  const reset = useCallback(() => {
    running.current = false
    referenceRef.current = null
    setPhase('IDLE')
    setMessage(null)
  }, [])

  const refreshTrade = useCallback(
    (trade: Trade) => {
      queryClient.setQueryData(tradeKeys.detail(trade.tradeId), trade)
      void queryClient.invalidateQueries({ queryKey: tradeKeys.lists() })
      // 취소는 판매 가능 수량을 되돌린다. 목록이 그대로면 사용자는 이미 되살아난
      // 수량을 못 보거나, 반대로 없는 수량을 보고 있게 된다.
      void queryClient.invalidateQueries({ queryKey: marketplaceKeys.all })
    },
    [queryClient],
  )

  /**
   * 결제 시작.
   *
   * 연동 후에는 여기가 서버의 `ready` 를 부르고 결제창으로 이동하는 자리다.
   *
   *   const { tid, redirectUrl } = await preparePayment(tradeId)
   *   referenceRef.current = tid
   *   sessionStorage.setItem('payment.tradeId', String(tradeId))
   *   window.location.href = redirectUrl
   *
   * sessionStorage 가 필요한 이유 — 페이지를 떠났다 돌아오면 메모리 상태가 사라진다.
   * 복귀 URL 쿼리스트링에 담을 수도 있지만, 그러면 사용자가 주소를 고쳐 다른 거래를
   * 승인시킬 수 있다.
   *
   * 지금은 비동기 호출이 없어 async 가 아니다.
   */
  const start = useCallback((tradeId: number) => {
    if (running.current) return
    referenceRef.current = `MOCK-TID-${tradeId}-${Date.now()}`
    setPhase('AWAITING_PG')
    setMessage(null)
  }, [])

  const confirm = useCallback(
    async (tradeId: number, amount: number) => {
      if (running.current) return
      running.current = true
      setPhase('CONFIRMING')

      try {
        const paymentReference = referenceRef.current
        if (!paymentReference) {
          throw new Error('결제 정보를 찾을 수 없습니다. 처음부터 다시 시도해 주세요.')
        }

        // 금액을 그대로 보내면 서버가 거래 금액과 대조한다. 어긋나면
        // PAYMENT_AMOUNT_MISMATCH 로 거절된다 — 화면이 계산을 잘못한 경우를 잡는 지점이다.
        const trade = await confirmPayment(tradeId, { paymentReference, paymentAmount: amount })
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
    start,
    confirm,
    cancel,
    reset,
  }
}
