import { useMemo, useState, type FormEvent } from 'react'
import { ErrorMessage } from '../../../components/common/index.ts'
import { usePredictionMarkets } from '../../prediction/hooks/usePredictionMarkets.ts'
import { useCreatePredictionMarket } from '../hooks/useCreatePredictionMarket.ts'
import './prediction-market-admin.css'

function toLocalInput(date: Date): string {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}

function afterHours(hours: number): string {
  return toLocalInput(new Date(Date.now() + hours * 60 * 60 * 1_000))
}

function asServerDate(value: string): string {
  return value.length === 16 ? `${value}:00` : value
}

export function PredictionMarketCreatePanel() {
  const create = useCreatePredictionMarket()
  const markets = usePredictionMarkets()
  const [productionBatchId, setProductionBatchId] = useState('')
  const [bettingEndsAt, setBettingEndsAt] = useState(() => afterHours(24))
  const [salesStartsAt, setSalesStartsAt] = useState(() => afterHours(48))
  const [salesEndsAt, setSalesEndsAt] = useState(() => afterHours(168))
  const [boundsText, setBoundsText] = useState('10, 20, 30')

  const bounds = useMemo(() => boundsText.split(',')
    .map((value) => Number(value.trim()))
    .filter((value) => Number.isInteger(value) && value > 0), [boundsText])

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    create.mutate({
      productionBatchId: Number(productionBatchId),
      bettingEndsAt: asServerDate(bettingEndsAt),
      salesStartsAt: asServerDate(salesStartsAt),
      salesEndsAt: asServerDate(salesEndsAt),
      bucketUpperBounds: bounds,
    })
  }

  return (
    <section className="admin-market" aria-labelledby="prediction-market-title">
      <header className="admin-market__header">
        <div>
          <p className="admin-market__eyebrow">SALES FORECAST</p>
          <h1 id="prediction-market-title">예측시장 개설</h1>
          <p>생산이 확정된 배치를 선택하고 베팅·판매 기간과 판매량 구간을 정합니다.</p>
        </div>
        <span className="admin-market__count">개설 {markets.data?.length ?? 0}건</span>
      </header>

      <form className="admin-market__form" onSubmit={submit}>
        <label>
          생산 배치 ID
          <input required min="1" type="number" value={productionBatchId}
            onChange={(event) => setProductionBatchId(event.target.value)} placeholder="예: 12" />
          <small>생산 확정과 온체인 등록이 끝난 배치만 개설할 수 있습니다.</small>
        </label>
        <div className="admin-market__dates">
          <label>베팅 마감<input required type="datetime-local" value={bettingEndsAt}
            onChange={(event) => setBettingEndsAt(event.target.value)} /></label>
          <label>판매 시작<input required type="datetime-local" value={salesStartsAt}
            onChange={(event) => setSalesStartsAt(event.target.value)} /></label>
          <label>판매 종료<input required type="datetime-local" value={salesEndsAt}
            onChange={(event) => setSalesEndsAt(event.target.value)} /></label>
        </div>
        <label>
          판매량 구간 상한
          <input required type="text" value={boundsText} onChange={(event) => setBoundsText(event.target.value)}
            placeholder="예: 10, 20, 30" />
          <small>쉼표로 구분하며 오름차순으로 입력합니다. 마지막 숫자는 해당 배치의 총 생산 수량이어야 합니다.</small>
        </label>

        <div className="admin-market__preview" aria-label="생성될 판매량 구간">
          {bounds.map((upper, index) => <span key={`${upper}-${index}`}>
            {index === 0 ? 0 : bounds[index - 1] + 1}~{upper}개
          </span>)}
        </div>

        <button type="submit" disabled={create.isPending || !productionBatchId || bounds.length < 2}>
          {create.isPending ? '온체인 개설 중…' : '예측시장 개설'}
        </button>
      </form>

      {create.error ? <ErrorMessage error={create.error} /> : null}
      {create.data ? <div className="admin-market__success" role="status">
        <strong>{create.data.productName}</strong> 예측시장이 열렸습니다.
        <span>시장 #{create.data.id} · 온체인 #{create.data.chainMarketId}</span>
      </div> : null}

      {markets.data?.length ? <div className="admin-market__list">
        <h2>최근 개설 시장</h2>
        {markets.data.slice(0, 5).map((market) => <article key={market.id}>
          <div><strong>{market.productName}</strong><span>생산 배치 #{market.productionBatchId}</span></div>
          <span className={`admin-market__status admin-market__status--${market.status.toLowerCase()}`}>{market.status}</span>
        </article>)}
      </div> : null}
    </section>
  )
}
