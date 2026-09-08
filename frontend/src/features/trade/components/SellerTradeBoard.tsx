import { useState } from 'react'
import { Button } from '../../../components/common/index.ts'
import { TradeList } from './TradeList.tsx'
import type { TradeStatus } from '../types/trade.types.ts'
import './trade.css'

/**
 * 판매자가 보는 상태 묶음.
 *
 * 서버의 `TradeStatus` 를 그대로 나열하지 않는다. 판매자에게 `CREATED`(결제 대기)는
 * 자기가 할 일이 없는 상태라 따로 걸러 볼 이유가 없고, `RECEIVED` 와 `COMPLETED` 는
 * 서버가 한 트랜잭션에서 연달아 넘기므로 `RECEIVED` 로 멈춘 거래는 사실상 존재하지 않는다.
 */
const FILTERS: readonly { label: string; status?: TradeStatus }[] = [
  { label: '전체' },
  { label: '발송 필요', status: 'PAID' },
  { label: '배송 중', status: 'SHIPPED' },
  { label: '거래 완료', status: 'COMPLETED' },
]

/**
 * 판매 거래 목록.
 *
 * `side=SELL` 을 고정한다 — 이 화면은 "내가 판 것" 만 다룬다. 구매 목록과 같은
 * `TradeList` 를 쓰지만 강조되는 상태가 다르다 (판매자에게는 결제 완료 건이 발송해야 할 건).
 */
export function SellerTradeBoard() {
  // 상태 필터는 이 컴포넌트 안에서만 쓰는 값이라 useState 다. 전역 스토어로 올리면
  // 다른 화면에서 남은 필터가 되돌아온다.
  const [status, setStatus] = useState<TradeStatus | undefined>(undefined)

  return (
    <>
      <div className="supplier-trades__filters" role="group" aria-label="거래 상태 필터">
        {FILTERS.map((filter) => {
          const selected = filter.status === status
          return (
            <Button
              key={filter.label}
              size="sm"
              variant={selected ? 'primary' : 'secondary'}
              aria-pressed={selected}
              onClick={() => setStatus(filter.status)}
            >
              {filter.label}
            </Button>
          )
        })}
      </div>

      <TradeList filters={{ side: 'SELL', status }} />
    </>
  )
}
