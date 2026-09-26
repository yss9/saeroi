import { useState } from 'react'
import { SellerTradeTable } from '../../features/supplier/components/SellerTradeTable.tsx'
import { useSellerTradeSummary } from '../../features/supplier/hooks/useSellerTradeSummary.ts'
import type { TradeStatus } from '../../features/trade/types/trade.types.ts'
import '../../components/common/console.css'

/** 탭. 서버의 `TradeStatus` 를 그대로 나열하지 않는다 — 아래 주석 참고. */
const TABS: readonly { label: string; status?: TradeStatus; key: 'all' | 'toShip' | 'shipping' | 'done' }[] = [
  { label: '전체', key: 'all' },
  { label: '발송 필요', status: 'PAID', key: 'toShip' },
  { label: '배송 중', status: 'SHIPPED', key: 'shipping' },
  { label: '거래 완료', status: 'COMPLETED', key: 'done' },
]

/**
 * 판매 거래 관리 (Supplier).
 *
 * 판매 등록은 `/supplier/listings` 가, 그 판매글에서 발생한 <b>거래</b>는 이 화면이 맡는다.
 * 한 화면에 합치지 않은 이유는 두 일의 주기가 다르기 때문이다 — 판매글은 한 번 올리고
 * 놔두지만, 거래는 결제될 때마다 발송이라는 할 일이 생긴다.
 *
 * <b>이 화면의 질문은 하나다: "오늘 뭘 보내야 하나".</b> 그래서 탭에 건수를 붙이고
 * 발송할 행만 색을 준다. 지표 타일이나 정렬 드롭다운은 두지 않는다 — 보통 발송할 건이
 * 한둘이고, 장치를 늘리면 그 한둘이 묻힌다.
 *
 * 탭에 결제 대기(`CREATED`)가 없는 것은 판매자가 할 일이 없는 상태라서다. `RECEIVED` 도
 * 없다 — 서버가 `COMPLETED` 로 연달아 넘기므로 그 상태로 멈춘 거래는 사실상 없다.
 */
export function SupplierTradePage() {
  const [status, setStatus] = useState<TradeStatus | undefined>(undefined)
  const [page, setPage] = useState(0)
  const summary = useSellerTradeSummary()

  function changeTab(next: TradeStatus | undefined) {
    setStatus(next)
    // 3쪽을 보다가 탭을 바꾸면 결과가 한 쪽으로 줄어들 수 있고, 그러면 3쪽은 빈 화면이라
    // "없다" 로 읽힌다.
    setPage(0)
  }

  return (
    <main className="console">
      <header className="console__head">
        <div>
          {/* 사이드바가 이미 "판매 거래" 를 켜고 있다. 큰 제목은 같은 말을 두 번 하는
              것이라 감추되 DOM 에는 남긴다 — 좁은 화면과 보조 기술에는 이것이 유일한
              화면 이름이다. */}
          <h1 className="ui-visually-hidden">판매 거래</h1>
          <p className="console__sub">
            {summary.isPending ? (
              '거래를 불러오는 중'
            ) : summary.toShip > 0 ? (
              <>
                보낼 것이 <b className="console__sub--todo">{summary.toShip}건</b> 있습니다
              </>
            ) : (
              '보낼 것이 없습니다'
            )}
          </p>
        </div>
      </header>

      <div className="console-tabs" role="group" aria-label="거래 상태">
        {TABS.map((tab) => (
          <button
            key={tab.label}
            type="button"
            className={`console-tab ${tab.key === 'toShip' && summary.toShip > 0 ? 'console-tab--todo' : ''}`}
            aria-pressed={tab.status === status}
            onClick={() => changeTab(tab.status)}
          >
            {tab.label}
            <span className="console-tab__count">{summary.isPending ? '—' : summary[tab.key]}</span>
          </button>
        ))}
      </div>

      <SellerTradeTable status={status} page={page} onPageChange={setPage} />
    </main>
  )
}
