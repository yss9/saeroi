import { useParams } from 'react-router-dom'
import { ErrorMessage, Loading } from '../../components/common/index.ts'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { PaymentPanel } from '../../features/trade/components/PaymentPanel.tsx'
import { TradeChainActionPanel } from '../../features/trade/components/TradeChainActionPanel.tsx'
import { TradeChainPanel } from '../../features/trade/components/TradeChainPanel.tsx'
import { ReceiptPanel } from '../../features/trade/components/ReceiptPanel.tsx'
import { ShipmentPanel } from '../../features/trade/components/ShipmentPanel.tsx'
import { TradeTimeline } from '../../features/trade/components/TradeTimeline.tsx'
import { useTrade } from '../../features/trade/hooks/useTrade.ts'
import { formatKg, formatWon } from '../../utils/quantity.ts'

/**
 * 거래 상세.
 *
 * **판매자와 구매자가 같은 주소를 쓰고 서로 다른 것을 한다.** 거래에는 양쪽이 각자
 * 해야 할 일이 있고(구매자는 결제·수령 확인, 판매자는 발송), 서버도 한 엔드포인트로
 * 양쪽에 상세를 내려준다. 그래서 주소를 나누지 않고 여기서 당사자를 구분해 패널을 고른다.
 *
 * 구분 기준은 **역할(Role)이 아니라 이 거래에서의 위치**다. `SUPPLIER` 라도 남의 거래의
 * 판매자가 아니면 발송할 수 없고(서버가 `seller_id` 로 대조한다), 정책 07.1 대로
 * Brand 재판매가 열리면 `BRAND` 도 판매자 자리에 선다. 역할로 갈랐다면 그때 이 화면이
 * 조용히 틀린다.
 */
export function TradeDetailPage() {
  const { tradeId } = useParams<{ tradeId: string }>()
  const parsed = Number(tradeId)
  const id = Number.isInteger(parsed) && parsed > 0 ? parsed : null

  const { user } = useAuth()
  const { data, isPending, isError, error, refetch } = useTrade(id)

  if (id === null) return <ErrorMessage error="잘못된 주소입니다." />
  if (isPending) return <Loading variant="skeleton" />
  if (isError) return <ErrorMessage error={error} onRetry={() => void refetch()} />

  const isSeller = user?.id === data.sellerId
  const isBuyer = user?.id === data.buyerId

  return (
    <main className="page">
      <header className="page__header">
        <h1>{data.materialName}</h1>
        <p className="page__lead">{data.tradeCode}</p>
      </header>

      <dl className="trade-detail__meta">
        <div>
          <dt>거래 수량</dt>
          <dd>{formatKg(data.quantity)}</dd>
        </div>
        <div>
          <dt>kg당 단가</dt>
          <dd>{formatWon(data.pricePerKg)}</dd>
        </div>
        <div>
          <dt>판매자</dt>
          <dd>{data.sellerName}</dd>
        </div>
        {/* 판매자에게는 "누가 샀는지" 가 발송에 필요한 정보다. */}
        <div>
          <dt>구매자</dt>
          <dd>{data.buyerName}</dd>
        </div>
      </dl>

      {/* 진행 상태는 양쪽이 같은 것을 본다. 무엇이 끝났고 지금 누구를 기다리는지가
          결제·발송 버튼보다 먼저 보여야 한다 — 사용자가 이 화면에 오는 이유다. */}
      <TradeTimeline trade={data} viewer={isSeller ? 'SELLER' : isBuyer ? 'BUYER' : undefined} />

      {/* 구매자: 결제 → 수령 확인. 두 패널을 함께 두는 이유는 결제 직후 다음에 무엇을
          기다려야 하는지가 같은 화면에 보여야 하기 때문이다. */}
      {isBuyer && (
        <>
          <PaymentPanel trade={data} />
          {/* 온체인 주문은 결제와 별개다 — 컨트랙트에 금액이 없고(정책 v3), 체인이 남기는
              것은 "누가 무엇을 얼마나 사기로 했는가" 뿐이다. 그래서 결제 실패와 체인 실패가
              서로를 막지 않도록 패널을 나란히 둔다. */}
          <TradeChainPanel trade={data} />
          <ReceiptPanel trade={data} />
          <TradeChainActionPanel trade={data} viewer="BUYER" />
        </>
      )}

      {/* 판매자: 발송. 결제 패널을 보여주지 않는다 — 판매자가 부를 수 있는 요청이 아니고,
          "결제하기" 버튼이 보이면 자기가 결제해야 하는 것처럼 읽힌다. */}
      {isSeller && (
        <>
          <ShipmentPanel trade={data} />
          <TradeChainActionPanel trade={data} viewer="SELLER" />
        </>
      )}

      {/* 당사자가 아니면 서버가 403 을 주므로 여기까지 오지 않는다. 그래도 로그인 정보가
          아직 복원되지 않은 순간이 있어(persist 복원 전) 아무 패널도 없는 화면이 잠깐
          보일 수 있다. 그때 빈 화면을 두면 고장으로 읽힌다. */}
      {!isBuyer && !isSeller && (
        <p className="page__lead">이 거래의 당사자만 결제·발송·수령 확인을 할 수 있습니다.</p>
      )}
    </main>
  )
}
