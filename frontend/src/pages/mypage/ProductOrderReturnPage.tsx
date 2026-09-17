import { useEffect, useRef, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { ErrorMessage, Loading } from '../../components/common/index.ts'
import { productKeys } from '../../features/product/api/productKeys.ts'
import { consumerOrderKeys } from '../../features/user/api/consumerOrderKeys.ts'
import {
  approveProductOrderPayment,
  cancelProductOrder,
} from '../../features/user/api/productOrderPaymentApi.ts'
import { PAYMENT_ORDER_KEY } from '../../features/user/hooks/useProductOrderPayment.ts'
import { toErrorMessage, type AppError } from '../../utils/errors.ts'

/**
 * 제품 주문 결제창에서 돌아오는 화면.
 *
 * 카카오는 승인·취소·실패 세 경우를 **같은 주소**로 돌려보낸다. 구분은 쿼리스트링이다.
 *
 *   승인  ?pg_token=xxxx   → 승인 요청을 보낸다 (여기서 실제 청구가 끝난다)
 *   취소  (pg_token 없음)  → 주문을 취소해 묶인 개체를 되돌린다
 *
 * ⚠️ **취소 경로에서 아무것도 하지 않으면 안 된다.** 주문을 만드는 순간 개체가 `RESERVED`
 * 로 잡히고, 제품 개체는 한 점씩이라 **한 건만 묶여도 그 제품이 즉시 품절로 보인다.**
 * 서버에 30분 마감 배치가 있지만 그때까지는 아무도 그 제품을 살 수 없다.
 *
 * 주소가 `/mypage/...` 가 아니라 `/orders/...` 인 것은 서버 설정이 그렇게 잡혀 있기
 * 때문이다 (`app.payment.kakao.product-order-return-url`). **두 값은 반드시 같아야 한다** —
 * 어긋나면 결제를 마친 사용자가 없는 페이지에 떨어지고, 취소도 승인도 일어나지 않는다.
 */
export function ProductOrderReturnPage() {
  const { orderId: orderIdParam } = useParams<{ orderId: string }>()
  const [searchParams] = useSearchParams()
  const queryClient = useQueryClient()

  const orderId = Number(orderIdParam ?? readStoredOrderId())
  const pgToken = searchParams.get('pg_token')
  const hasOrderId = Number.isFinite(orderId) && orderId > 0

  const [status, setStatus] = useState<'WORKING' | 'SUCCEEDED' | 'CANCELED' | 'FAILED'>(() =>
    hasOrderId ? 'WORKING' : 'FAILED',
  )
  const [message, setMessage] = useState<string | null>(() =>
    hasOrderId ? null : '어느 주문의 결제인지 확인할 수 없습니다.',
  )

  // pg_token 은 한 번만 쓸 수 있다. StrictMode 가 effect 를 두 번 실행하므로 두 번째
  // 승인은 PAYMENT_STATE_INVALID 로 거절되고, 사용자는 성공한 결제를 실패로 보게 된다.
  const handled = useRef(false)

  useEffect(() => {
    if (!hasOrderId || handled.current) return
    handled.current = true

    const refresh = () => {
      void queryClient.invalidateQueries({ queryKey: consumerOrderKeys.all })
      // 개체가 팔리거나 풀리면 남은 수량이 바뀐다.
      void queryClient.invalidateQueries({ queryKey: productKeys.all })
      clearStoredOrderId()
    }

    if (pgToken) {
      approveProductOrderPayment(orderId, pgToken)
        .then(() => {
          setStatus('SUCCEEDED')
          refresh()
        })
        .catch((error: AppError) => {
          setStatus('FAILED')
          setMessage(toErrorMessage(error))
        })
      return
    }

    cancelProductOrder(orderId)
      .then(() => {
        setStatus('CANCELED')
        refresh()
      })
      .catch((error: AppError) => {
        // 취소까지 실패하면 개체가 묶인 채 남는다. 조용히 넘기면 아무도 모른다 —
        // 서버 배치가 정리할 때까지 그 제품은 품절로 보인다.
        setStatus('FAILED')
        setMessage(toErrorMessage(error))
      })
  }, [hasOrderId, orderId, pgToken, queryClient])

  return (
    <main className="page payment-return">
      {status === 'WORKING' && (
        <Loading label={pgToken ? '결제를 승인하고 있습니다' : '주문을 취소하고 있습니다'} />
      )}

      {status === 'SUCCEEDED' && (
        <>
          <h1>결제가 완료되었습니다</h1>
          <p className="page__lead">
            주문 내역에서 배송 현황을 확인할 수 있습니다. 제품 여정은 결제가 끝난 지금부터
            열립니다.
          </p>
        </>
      )}

      {status === 'CANCELED' && (
        <>
          <h1>결제가 취소되었습니다</h1>
          <p className="page__lead">묶여 있던 제품은 다시 구매 가능 상태로 돌아갔습니다.</p>
        </>
      )}

      {status === 'FAILED' && (
        <>
          <h1>결제를 완료하지 못했습니다</h1>
          <ErrorMessage error={message ?? '알 수 없는 오류가 발생했습니다.'} />
          <p className="page__lead">주문 내역에서 상태를 확인해 주세요.</p>
        </>
      )}

      {status !== 'WORKING' && (
        <div className="payment-return__actions">
          <Link className="ui-button ui-button--primary ui-button--md" to="/mypage/orders">
            주문 내역 보기
          </Link>
          <Link className="ui-button ui-button--secondary ui-button--md" to="/shop">
            쇼핑 계속하기
          </Link>
        </div>
      )}
    </main>
  )
}

function readStoredOrderId(): string | null {
  try {
    return sessionStorage.getItem(PAYMENT_ORDER_KEY)
  } catch {
    return null
  }
}

function clearStoredOrderId(): void {
  try {
    sessionStorage.removeItem(PAYMENT_ORDER_KEY)
  } catch {
    // 지우지 못해도 다음 결제가 값을 덮어쓴다.
  }
}
