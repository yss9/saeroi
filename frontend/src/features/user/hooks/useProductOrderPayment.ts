// 제품 주문 결제 훅.
//
// 결제는 "요청 하나 보내고 끝" 이 아니라 **사이트를 떠났다 돌아오는 흐름**이다.
//
//   주문 생성 → ready → 결제창으로 이동(사이트를 떠남) → 인증 → 복귀 → approve → 완료
//
// **떠나는 순간 이 훅의 상태는 사라진다.** 그래서 복귀 후 승인은 이 훅이 아니라 복귀 화면
// (`ProductOrderReturnPage`)이 담당한다. 여기 남는 것은 "떠나기 전" 과 "결제 키가 없어
// 떠나지 않은 경우" 다. 소재 거래의 `usePayment` 와 같은 구조다.

import { useCallback, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { orderGroupRedirect } from '../../cart/orderGroupRedirect.ts'
import { toErrorMessage, type AppError } from '../../../utils/errors.ts'
import { productKeys } from '../../product/api/productKeys.ts'
import { consumerOrderKeys } from '../api/consumerOrderKeys.ts'
import { createProductOrder } from '../api/createProductOrder.ts'
import {
  approveProductOrderPayment,
  cancelProductOrder,
  readyProductOrderPayment,
} from '../api/productOrderPaymentApi.ts'
import type { ProductOrderPaymentPhase } from '../types/consumerOrder.types.ts'

/** 결제창에서 돌아왔을 때 어느 주문이었는지 복원하려고 남긴다. */
export const PAYMENT_ORDER_KEY = 'payment.productOrderId'

export interface UseProductOrderPaymentResult {
  phase: ProductOrderPaymentPhase
  message: string | null
  isBusy: boolean
  /** 준비를 마친 뒤에만 값이 있다. false 면 결제창이 열리지 않는다 */
  live: boolean | null
  /** 결제 키가 없는 환경에서 승인 버튼을 띄울 때 쓴다 */
  orderId: number | null
  /** 주문 생성 → 결제 준비 → (실제 PG 면) 결제창으로 이동 */
  purchase: (input: { productModelId: number; addressId: number }) => Promise<void>
  /** 결제 키가 없는 환경에서만 쓴다. 실제 PG 는 복귀 화면이 승인한다 */
  approve: () => Promise<void>
  reset: () => void
}

export function useProductOrderPayment(): UseProductOrderPaymentResult {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [phase, setPhase] = useState<ProductOrderPaymentPhase>('IDLE')
  const [message, setMessage] = useState<string | null>(null)
  const [live, setLive] = useState<boolean | null>(null)
  const [orderId, setOrderId] = useState<number | null>(null)

  // 중복 실행 방지. 구매 버튼을 두 번 누르면 주문이 두 건 만들어지고, 개체가 한 점씩이라
  // 두 번째는 품절로 거절되거나 남의 몫을 묶는다. state 로 막으면 반영 전 두 번째 클릭을 놓친다.
  const running = useRef(false)

  const refresh = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: consumerOrderKeys.all })
    // 개체가 묶이거나 풀리면 남은 수량이 바뀐다. 방금 본 제품 상세가 옛 값으로 남으면
    // 사용자는 자기 주문이 반영되지 않았다고 읽는다.
    void queryClient.invalidateQueries({ queryKey: productKeys.all })
  }, [queryClient])

  const reset = useCallback(() => {
    running.current = false
    setPhase('IDLE')
    setMessage(null)
    setLive(null)
    setOrderId(null)
  }, [])

  const purchase = useCallback(
    async (input: { productModelId: number; addressId: number }) => {
      if (running.current) return
      running.current = true
      setPhase('CREATING')
      setMessage(null)

      let created: number | null = null
      try {
        const order = await createProductOrder(input)
        refresh()

        // **서버가 주문 번호를 주지 않으면 결제를 이어갈 수 없다.** 결제 경로가 {orderId}
        // 를 받는데 주문 응답에는 orderCode 만 있다(추가 요청해 둔 상태). 주문은 이미
        // 만들어졌고 개체가 묶였는데 취소조차 번호로 부르는 경로라, 여기서는 사실을
        // 알리는 것 말고 할 수 있는 일이 없다 — 서버의 30분 마감 배치가 정리한다.
        if (!Number.isInteger(order.orderId) || order.orderId <= 0) {
          setPhase('FAILED')
          setMessage(
            '주문은 만들어졌지만 결제를 이어갈 수 없습니다. 잠시 후 주문 내역에서 상태를 확인해 주세요.',
          )
          return
        }

        created = order.orderId
        setOrderId(order.orderId)

        setPhase('PREPARING')
        const ready = await readyProductOrderPayment(order.orderId)
        setLive(ready.live)
        setPhase('AWAITING_PG')

        if (ready.redirectUrl) {
          try {
            sessionStorage.setItem(PAYMENT_ORDER_KEY, String(order.orderId))
          } catch {
            // 사파리 프라이빗 모드 등에서 막힌다. 복귀 URL 에 주문 id 가 있으므로
            // 저장 실패가 결제를 막을 이유는 없다.
          }
          window.location.href = ready.redirectUrl
        }
        // redirectUrl 이 없으면 결제 키가 없는 환경이다. 화면이 승인 버튼을 띄운다.
      } catch (error) {
        const target = orderGroupRedirect(error)
        if (target) { navigate(target, { replace: true }); return }
        setPhase('FAILED')
        setMessage(toErrorMessage(error as AppError))

        // **주문은 만들어졌는데 결제 준비가 실패한 경우를 그대로 두면 안 된다.** 개체가
        // RESERVED 로 묶인 채 남고, 한 점씩인 제품이라 그 제품은 즉시 품절로 보인다.
        if (created !== null) {
          await cancelProductOrder(created).catch((cancelError: unknown) => {
            const target = orderGroupRedirect(cancelError)
            if (target) navigate(target, { replace: true })
            // 취소까지 실패하면 서버의 30분 마감 배치가 정리한다. 여기서 더 할 일이 없다.
          })
          setOrderId(null)
          refresh()
        }
      } finally {
        running.current = false
      }
    },
    [refresh, navigate],
  )

  /**
   * 결제 키가 없는 환경의 승인.
   *
   * 실제 PG 에서는 쓰이지 않는다 — 브라우저가 카카오페이로 떠났다가 복귀 URL 로 돌아오고,
   * 그 화면이 `pg_token` 을 읽어 승인한다. 여기서 보내는 토큰은 검사하지 않는 자리값이다.
   */
  const approve = useCallback(async () => {
    if (running.current || orderId === null) return
    running.current = true
    setPhase('CONFIRMING')

    try {
      await approveProductOrderPayment(orderId, 'MOCK-PG-TOKEN')
      refresh()
      setPhase('SUCCEEDED')
      setMessage(null)
    } catch (error) {
      const target = orderGroupRedirect(error)
      if (target) { navigate(target, { replace: true }); return }
      setPhase('FAILED')
      setMessage(toErrorMessage(error as AppError))
    } finally {
      running.current = false
    }
  }, [orderId, refresh, navigate])

  return {
    phase,
    message,
    isBusy: phase === 'CREATING' || phase === 'PREPARING' || phase === 'AWAITING_PG' || phase === 'CONFIRMING',
    live,
    orderId,
    purchase,
    approve,
    reset,
  }
}
