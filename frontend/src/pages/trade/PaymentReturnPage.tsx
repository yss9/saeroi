import { useEffect, useRef, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { ErrorMessage, Loading } from '../../components/common/index.ts'
import { marketplaceKeys } from '../../features/marketplace/api/marketplaceKeys.ts'
import { approvePayment, cancelTrade } from '../../features/trade/api/paymentApi.ts'
import { tradeKeys } from '../../features/trade/api/tradeKeys.ts'
import { PAYMENT_TRADE_KEY } from '../../features/trade/hooks/usePayment.ts'
import { toErrorMessage, type AppError } from '../../utils/errors.ts'
import { formatWon } from '../../utils/quantity.ts'

/**
 * 카카오페이 결제창에서 돌아오는 화면.
 *
 * 카카오는 승인·취소·실패 세 경우를 **같은 주소**로 돌려보낸다. 구분은 쿼리스트링이다.
 *
 *   승인  ?pg_token=xxxx        → 승인 요청을 보낸다 (여기서 실제 청구가 끝난다)
 *   취소  (pg_token 없음)       → 거래를 취소해 판매 수량을 되돌린다
 *
 * **취소 경로에서 아무것도 하지 않으면 안 된다.** 구매 요청 시점에 판매글의 잔여 수량이
 * 이미 줄어 있어서, 그대로 두면 그 수량이 아무도 살 수 없는 채로 남는다.
 *
 * 거래 id 는 경로에서 읽는다. sessionStorage 값은 경로가 없을 때의 대비책이다.
 */
export function PaymentReturnPage() {
  const { tradeId: tradeIdParam } = useParams<{ tradeId: string }>()
  const [searchParams] = useSearchParams()
  const queryClient = useQueryClient()

  const tradeId = Number(tradeIdParam ?? readStoredTradeId())
  const pgToken = searchParams.get('pg_token')

  // 거래 id 유효성은 렌더 시점에 이미 알 수 있다. effect 안에서 setState 로 처리하면
  // 불필요한 렌더가 한 번 더 돌고, "어느 거래인지 모른다" 를 잠깐 로딩으로 보여주게 된다.
  const hasTradeId = Number.isFinite(tradeId) && tradeId > 0

  const [status, setStatus] = useState<'WORKING' | 'SUCCEEDED' | 'CANCELED' | 'FAILED'>(() =>
    hasTradeId ? 'WORKING' : 'FAILED',
  )
  const [message, setMessage] = useState<string | null>(() =>
    hasTradeId ? null : '어느 거래의 결제인지 확인할 수 없습니다.',
  )
  const [paidAmount, setPaidAmount] = useState<number | null>(null)

  // pg_token 은 한 번만 쓸 수 있다. StrictMode 가 effect 를 두 번 실행하므로
  // 두 번째 승인 요청은 서버에서 PAYMENT_STATE_INVALID 로 거절되고, 사용자는 성공한
  // 결제를 실패로 보게 된다. 그래서 실행을 ref 로 한 번만 막는다.
  const handled = useRef(false)

  useEffect(() => {
    if (!hasTradeId || handled.current) return
    handled.current = true

    const refresh = () => {
      void queryClient.invalidateQueries({ queryKey: tradeKeys.all })
      void queryClient.invalidateQueries({ queryKey: marketplaceKeys.all })
      clearStoredTradeId()
    }

    if (pgToken) {
      approvePayment(tradeId, pgToken)
        .then((trade) => {
          setPaidAmount(trade.paymentAmount)
          setStatus('SUCCEEDED')
          refresh()
        })
        .catch((error: AppError) => {
          setStatus('FAILED')
          setMessage(toErrorMessage(error))
        })
      return
    }

    // 결제창에서 취소하거나 실패한 경로. 예약된 수량을 되돌린다.
    cancelTrade(tradeId)
      .then(() => {
        setStatus('CANCELED')
        refresh()
      })
      .catch((error: AppError) => {
        // 취소까지 실패하면 수량이 묶인 채로 남는다. 사용자에게 거래 화면으로 가서
        // 직접 취소하라고 알려야 한다 — 조용히 넘기면 아무도 모른다.
        setStatus('FAILED')
        setMessage(toErrorMessage(error))
      })
  }, [hasTradeId, pgToken, queryClient, tradeId])

  return (
    <main className="page payment-return">
      {status === 'WORKING' && (
        <Loading label={pgToken ? '결제를 승인하고 있습니다' : '결제를 취소하고 있습니다'} />
      )}

      {status === 'SUCCEEDED' && (
        <>
          <h1>결제가 완료되었습니다</h1>
          {paidAmount !== null && (
            <p className="payment-return__amount">{formatWon(paidAmount)}</p>
          )}
          {/* 결제가 끝나도 소재는 아직 넘어오지 않는다. 이 순서를 알려주지 않으면
              "결제했는데 왜 내 재고에 없지" 가 된다. */}
          <p className="page__lead">
            판매자가 발송하면 알려드립니다. 소재는 수령 확인 시점에 내 보유량으로 넘어옵니다.
          </p>
        </>
      )}

      {status === 'CANCELED' && (
        <>
          <h1>결제가 취소되었습니다</h1>
          <p className="page__lead">예약된 수량은 다시 판매 가능 상태로 돌아갔습니다.</p>
        </>
      )}

      {status === 'FAILED' && (
        <>
          <h1>결제를 완료하지 못했습니다</h1>
          <ErrorMessage error={message ?? '알 수 없는 오류가 발생했습니다.'} />
          <p className="page__lead">
            거래 상세에서 상태를 확인하고 필요하면 직접 취소해 주세요.
          </p>
        </>
      )}

      {/* Button 은 <button> 전용이라 이동에는 Link 를 그대로 쓴다.
          공용 컴포넌트를 다형(as)으로 바꾸는 것은 이 화면 하나 때문에 할 일이 아니다. */}
      {status !== 'WORKING' && (
        <div className="payment-return__actions">
          {hasTradeId && (
            <Link className="ui-button ui-button--primary ui-button--md" to={`/trades/${tradeId}`}>
              거래 상세 보기
            </Link>
          )}
          <Link className="ui-button ui-button--secondary ui-button--md" to="/marketplace">
            마켓플레이스로
          </Link>
        </div>
      )}
    </main>
  )
}

function readStoredTradeId(): string | null {
  try {
    return sessionStorage.getItem(PAYMENT_TRADE_KEY)
  } catch {
    return null
  }
}

function clearStoredTradeId(): void {
  try {
    sessionStorage.removeItem(PAYMENT_TRADE_KEY)
  } catch {
    // 지우지 못해도 다음 결제가 값을 덮어쓴다.
  }
}
