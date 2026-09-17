import { useState } from 'react'
import { Button, Modal } from '../../../components/common/index.ts'
import { TradeDetail } from './TradeDetail.tsx'
import { TradeList } from './TradeList.tsx'
import type { TradeStatus } from '../types/trade.types.ts'
import './trade.css'

/**
 * 구매자가 보는 상태 묶음.
 *
 * 서버의 `TradeStatus` 를 그대로 나열하지 않는다. 구매자에게 `PAID`(결제 완료)는 판매자가
 * 보내 주기를 기다리는 구간이라 "발송 대기" 로 읽어야 하고, `RECEIVED` 는 서버가
 * `COMPLETED` 로 연달아 넘기므로 그 상태로 멈춘 거래는 사실상 없다.
 *
 * 순서는 **거래가 흘러가는 순서**다 — 결제 → 발송 대기 → 배송 중 → 완료. 그래야 지금 내
 * 거래가 어디쯤인지 목록을 보기 전에 눈으로 짚을 수 있다.
 */
const FILTERS: readonly { label: string; status?: TradeStatus }[] = [
  { label: '전체' },
  { label: '결제 필요', status: 'CREATED' },
  { label: '발송 대기', status: 'PAID' },
  { label: '배송 중', status: 'SHIPPED' },
  { label: '거래 완료', status: 'COMPLETED' },
  // 취소는 끝난 거래라 맨 뒤다. 앞에 두면 진행 중인 거래를 찾는 눈길을 가로챈다.
  { label: '거래 취소', status: 'CANCELED' },
]

/**
 * 구매 내역.
 *
 * `side` 를 보내지 않아 서버 기본값 `BUY` 가 쓰인다 — 이 화면은 내가 산 것만 다룬다.
 *
 * **거르는 축이 상태뿐인 것은 서버가 그것만 받기 때문이다.** `GET /v1/trades` 에는
 * 소재 종류·등급·검색어 파라미터가 없고, 거래 응답에도 그 필드가 없다. 목록은 서버가
 * 쪽수로 잘라 주므로 화면에서 걸러 봐야 지금 쪽에 있는 것만 걸러져, "없다" 가 거짓이 된다.
 */
export function BuyerTradeBoard() {
  // 이 화면 안에서만 쓰는 값이라 useState 다. 전역으로 올리면 다른 화면에서 남은 필터가
  // 되돌아온다 (판매 거래 쪽과 같은 판단이다).
  const [status, setStatus] = useState<TradeStatus | undefined>(undefined)
  // 상세는 팝업으로 연다. 목록에서 한 건 확인하고 다음 건으로 넘어가는 화면이라,
  // 페이지를 갈아 끼우면 볼 때마다 목록으로 돌아오는 걸음이 하나씩 더 붙는다.
  const [opened, setOpened] = useState<number | null>(null)

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

      <TradeList filters={{ status }} onSelect={setOpened} />

      <Modal
        open={opened !== null}
        title="거래 상세"
        onClose={() => setOpened(null)}
      >
        {opened !== null && <TradeDetail tradeId={opened} />}
      </Modal>
    </>
  )
}
