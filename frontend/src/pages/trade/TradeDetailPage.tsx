import { useParams } from 'react-router-dom'
import { BackLink, ErrorMessage } from '../../components/common/index.ts'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
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
 * 목록에서는 팝업으로 뜨지만(`BuyerTradeBoard`), 결제·발송 콜백이 이 주소로 돌아오고
 * 알림도 이 주소를 가리켜 페이지 자체는 남는다.
 *
 * **돌아갈 곳은 역할이 아니라 이 거래에서의 위치로 고른다.** 정책 07.1 로 재판매가 열리면
 * BRAND 도 판매자 자리에 서는데, 그때 역할로 갈랐다면 방금 본 거래가 없는 목록이 열린다.
 *
 * 거래를 읽기 전에는 판매자인지 알 수 없어 그 구간만 역할로 고른다 — 구매 내역(/trades)은
 * BRAND 전용이라 판매자를 그리로 보내면 /403 이다. 같은 쿼리를 상세 본문과 나눠 쓰므로
 * 요청이 더 나가지는 않는다.
 */
export function TradeDetailPage() {
  const { tradeId } = useParams<{ tradeId: string }>()
  const parsed = Number(tradeId)
  const id = Number.isInteger(parsed) && parsed > 0 ? parsed : null

  const { user, role } = useAuth()
  const { data } = useTrade(id)
  const back =
    data === undefined
      ? role === 'BRAND'
        ? PURCHASES
        : SALES
      : user?.id === data.sellerId
        ? SALES
        : PURCHASES

  return (
    <main className="page">
      <BackLink {...back} />
      {id === null ? <ErrorMessage error="잘못된 주소입니다." /> : <TradeDetail tradeId={id} />}
    </main>
  )
}
