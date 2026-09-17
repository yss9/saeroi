import { useNavigate } from 'react-router-dom'
import { CaretRight } from '@phosphor-icons/react'
import { ErrorMessage, Loading, Pagination } from '../../../components/common/index.ts'
import { formatKg, formatWon } from '../../../utils/quantity.ts'
import { useTrades } from '../../trade/hooks/useTrades.ts'
import { TRADE_STATUS_LABEL, type Trade, type TradeStatus } from '../../trade/types/trade.types.ts'

export interface SellerTradeTableProps {
  status: TradeStatus | undefined
  page: number
  onPageChange: (page: number) => void
}

/**
 * 판매 거래 목록.
 *
 * <b>카드가 아니라 표다.</b> 거래는 필드가 똑같이 반복되는 기록이라, 카드로 늘어놓으면
 * 같은 값을 세로로 훑게 되고 비교가 안 된다. 예전 카드는 다섯 값(상태·소재·수량·금액·
 * 발송 필요)만 쓰고 응답에 오는 구매자·날짜·거래번호·송장을 전부 버려서, 판매자가
 * <b>누가 샀는지도 모른 채</b> 발송했다.
 *
 * <b>행은 두 종류뿐이다.</b> 손댈 것이 있는 행(발송 필요)과 나머지. 전부에 색을 주면
 * 급한 것이 묻힌다 — 이 화면에 오는 이유는 "오늘 뭘 보내야 하나" 하나다.
 */
export function SellerTradeTable({ status, page, onPageChange }: SellerTradeTableProps) {
  const navigate = useNavigate()
  const { data, isPending, isError, error, refetch } = useTrades({ side: 'SELL', status, page })

  if (isPending) return <Loading variant="skeleton" rows={4} label="판매 거래를 불러오는 중" />
  if (isError) return <ErrorMessage error={error} onRetry={() => void refetch()} />

  if (data.content.length === 0) {
    return (
      <div className="console-empty">
        <h3>아직 판매된 거래가 없습니다</h3>
        <p>등록한 판매글이 구매되면 여기에 표시됩니다.</p>
        <button type="button" className="console-btn console-btn--ghost" onClick={() => navigate('/supplier/listings')}>
          판매 관리로 가기
        </button>
      </div>
    )
  }

  // 발송할 것이 맨 위로, 그 안에서는 오래 기다린 것이 먼저.
  //
  // **이 정렬은 지금 쪽 안에서만이다.** 서버에 정렬 파라미터가 없어(`TradeFilters`)
  // 페이지를 넘기면 순서가 이어지지 않는다. 그래도 하는 편이 낫다 — 한 쪽에 다 들어오는
  // 규모이고, 발송할 건이 목록 한가운데 섞여 있는 것보다 낫다.
  const rows = [...data.content].sort(compareForSeller)

  return (
    <>
      <div className="console-table-wrap">
        <table className="console-table">
          <thead>
            <tr>
              <th scope="col">상태</th>
              <th scope="col">소재</th>
              <th scope="col">구매자</th>
              <th scope="col" className="is-num">수량</th>
              <th scope="col" className="is-num">금액</th>
              <th scope="col">날짜</th>
              <th scope="col">거래번호</th>
              <th scope="col"><span className="ui-visually-hidden">동작</span></th>
            </tr>
          </thead>
          <tbody>
            {rows.map((trade) => (
              <SellerTradeRow key={trade.tradeId} trade={trade} />
            ))}
          </tbody>
        </table>
      </div>

      {data.totalPages > 1 && (
        <div className="console-foot">
          <Pagination page={data} onChange={onPageChange} />
        </div>
      )}
    </>
  )
}

function SellerTradeRow({ trade }: { trade: Trade }) {
  const navigate = useNavigate()
  const toShip = trade.status === 'PAID'
  const canceled = trade.status === 'CANCELED'
  const detail = `/trades/${trade.tradeId}`

  return (
    <tr
      className={[
        'console-row',
        toShip ? 'console-row--todo' : '',
        canceled ? 'console-row--off' : '',
      ]
        .filter(Boolean)
        .join(' ')}
      onClick={() => navigate(detail)}
    >
      <td>
        <span className={`console-badge ${toShip ? 'console-badge--wait' : canceled ? 'console-badge--flat' : 'console-badge--ok'}`}>
          {toShip ? '발송 필요' : TRADE_STATUS_LABEL[trade.status]}
        </span>
      </td>

      <td className="console-row__name">{trade.materialName}</td>

      {/* 응답에 있는데 예전 카드가 버리던 값. 누가 샀는지 모르면 발송할 수 없다. */}
      <td>{trade.buyerName}</td>

      <td className="is-num">{formatKg(trade.quantity)}</td>
      <td className="is-num console-row__money">{formatWon(trade.totalPrice)}</td>

      <td className="console-row__when">
        {whenOf(trade)}
        {/* 배송 중이면 송장이 이 거래의 가장 중요한 부가정보다. */}
        {trade.status === 'SHIPPED' && invoiceOf(trade) && <small>{invoiceOf(trade)}</small>}
      </td>

      <td className="console-row__code">{trade.tradeCode}</td>

      <td className="console-row__act">
        {toShip ? (
          // 발송은 배송지·택배사·송장이 필요해 상세에서 한다. 여기서는 그 자리로
          // 곧장 보내는 것까지가 목록의 몫이다.
          <button
            type="button"
            className="console-btn console-btn--primary console-btn--sm"
            onClick={(event) => {
              event.stopPropagation()
              navigate(detail)
            }}
          >
            발송
          </button>
        ) : (
          <CaretRight aria-hidden="true" className="console-row__chevron" />
        )}
      </td>
    </tr>
  )
}

/**
 * 정렬 기준.
 *
 * 발송할 것 → 배송 중 → 끝난 것. 같은 묶음 안에서는 <b>오래 기다린 것이 먼저</b>다 —
 * 판매자가 먼저 손대야 하는 것이 그쪽이다.
 */
function compareForSeller(a: Trade, b: Trade): number {
  const rank = (trade: Trade) =>
    trade.status === 'PAID' ? 0 : trade.status === 'SHIPPED' ? 1 : trade.status === 'CANCELED' ? 3 : 2
  const byRank = rank(a) - rank(b)
  if (byRank !== 0) return byRank
  return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
}

/**
 * 날짜 칸.
 *
 * 상태마다 사용자가 궁금한 시각이 다르다 — 발송할 건은 "언제 결제됐나"(= 얼마나
 * 기다렸나)이고, 끝난 건은 "언제 끝났나" 다. 하나만 고르지 않으면 열이 비어 보인다.
 */
function whenOf(trade: Trade): string {
  if (trade.status === 'PAID' && trade.paidAt) {
    return `${shortDate(trade.paidAt)} 결제${elapsed(trade.paidAt)}`
  }
  if (trade.status === 'SHIPPED' && trade.shippedAt) return `${shortDate(trade.shippedAt)} 발송`
  if (trade.status === 'CANCELED' && trade.canceledAt) return `${shortDate(trade.canceledAt)} 취소`
  if (trade.receivedAt) return `${shortDate(trade.receivedAt)} 완료`
  return shortDate(trade.createdAt)
}

/** "· 2일 경과". 오늘이면 붙이지 않는다 — 급한 것만 눈에 걸려야 한다. */
function elapsed(iso: string): string {
  const days = Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000)
  return days >= 1 ? ` · ${days}일 경과` : ''
}

function shortDate(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  return `${date.getMonth() + 1}/${date.getDate()}`
}

function invoiceOf(trade: Trade): string | null {
  const shipping = trade.shipping
  if (!shipping?.trackingNumber) return null
  return shipping.carrier ? `${shipping.carrier} ${shipping.trackingNumber}` : shipping.trackingNumber
}
