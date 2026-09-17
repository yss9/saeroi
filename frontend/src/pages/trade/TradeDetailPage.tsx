import { useParams } from 'react-router-dom'
import { BackLink, ErrorMessage, Loading } from '../../components/common/index.ts'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { SellerTradeDetail } from '../../features/supplier/components/SellerTradeDetail.tsx'
import { TradeDetail } from '../../features/trade/components/TradeDetail.tsx'
import { useTrade } from '../../features/trade/hooks/useTrade.ts'

/**
 * 돌아갈 목록.
 *
 * 문구와 경로를 한 쌍으로 묶는다 — 따로 두면 "판매 거래" 라고 적힌 링크가 구매 내역으로
 * 가는 조합이 만들어진다.
 */
const PURCHASES = { to: '/trades', label: '구매 내역' } as const
const SALES = { to: '/supplier/trades', label: '판매 거래' } as const

/**
 * 거래 상세 — 주소로 바로 들어왔을 때.
 *
 * 구매자는 목록에서 팝업으로도 연다(`BuyerTradeBoard`). 결제·발송 콜백이 이 주소로
 * 돌아오고 알림도 이 주소를 가리켜 페이지 자체는 남는다.
 *
 * <b>본문을 당사자에 따라 가른다.</b> 같은 거래를 열지만 하는 일이 반대다 — 구매자는
 * 결제하고 받고(`TradeDetail`), 판매자는 보낸다(`SellerTradeDetail`). 구매자 본문은
 * 팝업 안에도 들어가야 해서 세로로 짧고, 판매자 본문은 배송지·송장을 적을 자리가
 * 넓은 2단이다. 한 컴포넌트로 합치면 둘 중 하나는 반드시 어색해진다.
 *
 * <b>가르는 기준은 역할이 아니라 이 거래에서의 위치다.</b> 정책 07.1 로 재판매가 열리면
 * BRAND 도 판매자 자리에 서는데, 역할로 갈랐다면 그때 이 화면이 조용히 틀린다.
 *
 * 거래를 읽기 전에는 판매자인지 알 수 없어 그 구간만 역할로 고른다 — 구매 내역(/trades)은
 * BRAND 전용이라 판매자를 그리로 보내면 /403 이다.
 */
export function TradeDetailPage() {
  const { tradeId } = useParams<{ tradeId: string }>()
  const parsed = Number(tradeId)
  const id = Number.isInteger(parsed) && parsed > 0 ? parsed : null

  const { user, role } = useAuth()
  const { data, isPending, isError, error, refetch } = useTrade(id)

  const isSeller = data !== undefined && user?.id === data.sellerId
  const back = data === undefined ? (role === 'BRAND' ? PURCHASES : SALES) : isSeller ? SALES : PURCHASES

  if (id === null) {
    return (
      <main className="page">
        <BackLink {...back} />
        <ErrorMessage error="잘못된 주소입니다." />
      </main>
    )
  }

  // 로딩·오류를 여기서 처리한다. 본문 둘이 껍데기를 달리 쓰므로(page / console) 어느
  // 쪽 껍데기로 그릴지 정하기 전에는 본문에 맡길 수 없다.
  if (isPending) {
    return (
      <main className="page">
        <BackLink {...back} />
        <Loading variant="skeleton" />
      </main>
    )
  }
  if (isError) {
    return (
      <main className="page">
        <BackLink {...back} />
        <ErrorMessage error={error} onRetry={() => void refetch()} />
      </main>
    )
  }

  if (isSeller) {
    return (
      <main className="console">
        <BackLink {...back} />
        <SellerTradeDetail trade={data} />
      </main>
    )
  }

  // 구매자와 제3자. 당사자가 아니면 서버가 403 을 주므로 본문이 그 사실을 말한다.
  return (
    <main className="page">
      <BackLink {...back} />
      <TradeDetail tradeId={id} />
    </main>
  )
}
