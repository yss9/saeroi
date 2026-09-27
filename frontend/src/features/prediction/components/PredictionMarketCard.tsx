import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { Button } from '../../../components/common/index.ts'
import { toErrorMessage } from '../../../utils/errors.ts'
import { usePredictionChainState } from '../hooks/usePredictionChainState.ts'
import { claimPrediction, formatPredictionPoint } from '../services/predictionContractService.ts'
import type { PredictionChainState, PredictionMarket } from '../types/prediction.types.ts'
import { MARKET_PHASE_LABEL, marketPhase, timeLeftText, type MarketPhase } from '../utils/marketPhase.ts'
import { PredictionBetForm } from './PredictionBetForm.tsx'

function formatDate(value: string) {
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function rangeOf(market: PredictionMarket, index: number) {
  const bucket = market.buckets.find((item) => item.index === index)
  return bucket ? `${bucket.lowerBound.toLocaleString()}~${bucket.upperBound.toLocaleString()}개` : `구간 ${index + 1}`
}

/**
 * 예측시장 하나.
 *
 * 카드가 답하는 질문은 단계마다 하나다 — 진행 중이면 "참여할까", 집계 중이면 "언제 나오나",
 * 결과가 나오면 "맞혔나, 받을 것이 있나". 모든 줄을 늘 보이면 참여하지 않은 사람에게도
 * "참여 포인트 0 · 내 수익 정산 전" 같은 빈 숫자가 뜬다.
 */
export function PredictionMarketCard({ market, walletAddress, balance, now, onChanged }: {
  market: PredictionMarket
  walletAddress: string | null
  balance: bigint | undefined
  now: number
  onChanged: () => void
}) {
  const phase = marketPhase(market, now)
  const chain = usePredictionChainState(market, walletAddress)
  const state = chain.data
  // 체인을 다시 읽기 전에도 참여한 것으로 둔다 — 그 사이 폼이 남아 있으면 한 번 더 건다.
  const [placed, setPlaced] = useState<{ surveyFailed: boolean } | null>(null)
  const participated = placed !== null || (state?.userStake ?? 0n) > 0n

  return (
    <article className={`prediction-card prediction-card--${phase.toLowerCase()}`}>
      <header className="prediction-card__head">
        {market.productThumbnailUrl
          ? <img src={market.productThumbnailUrl} alt="" className="prediction-card__thumb" />
          : <span className="prediction-card__thumb" aria-hidden="true" />}
        <div>
          <span className={`prediction-phase prediction-phase--${phase.toLowerCase()}`}>{MARKET_PHASE_LABEL[phase]}</span>
          <h3>{market.productName}</h3>
          <p>{market.unitPrice.toLocaleString()}원 · 생산 {market.productionQuantity.toLocaleString()}개</p>
        </div>
      </header>

      <Schedule market={market} phase={phase} now={now} />

      {participated && (
        <MyPrediction market={market} state={state} phase={phase} placed={placed} />
      )}

      {phase === 'OPEN' && !participated && (
        walletAddress ? (
          <PredictionBetForm
            market={market}
            state={state}
            balance={balance}
            onPlaced={(surveyFailed) => {
              setPlaced({ surveyFailed })
              void chain.refetch()
              onChanged()
            }}
          />
        ) : (
          <p className="prediction-hint prediction-card__locked">등록한 지갑을 연결하면 이 예측에 참여할 수 있습니다.</p>
        )
      )}

      {(phase === 'RESOLVED' || phase === 'CANCELED') && (
        <Outcome market={market} state={state} phase={phase} onClaimed={() => { void chain.refetch(); onChanged() }} />
      )}
    </article>
  )
}

function Schedule({ market, phase, now }: { market: PredictionMarket; phase: MarketPhase; now: number }) {
  if (phase === 'OPEN') {
    return (
      <p className="prediction-card__schedule">
        <strong>{timeLeftText(new Date(market.bettingEndsAt).getTime(), now)}</strong>
        <span>예측 마감 {formatDate(market.bettingEndsAt)} · 판매 집계 {formatDate(market.salesStartsAt)} ~ {formatDate(market.salesEndsAt)}</span>
      </p>
    )
  }
  if (phase === 'COUNTING') {
    return (
      <p className="prediction-card__schedule">
        <strong>예측이 마감됐어요</strong>
        <span>판매 집계 {formatDate(market.salesStartsAt)} ~ {formatDate(market.salesEndsAt)} · 집계가 끝나면 결과가 발표됩니다</span>
      </p>
    )
  }
  return (
    <p className="prediction-card__schedule">
      <span>판매 집계 {formatDate(market.salesStartsAt)} ~ {formatDate(market.salesEndsAt)}</span>
    </p>
  )
}

/** 내가 건 구간과 포인트. 결과 전에는 이것만이 참여했다는 증거다. */
function MyPrediction({ market, state, phase, placed }: {
  market: PredictionMarket
  state: PredictionChainState | undefined
  phase: MarketPhase
  placed: { surveyFailed: boolean } | null
}) {
  const picks = state
    ? state.userBucketStakes.flatMap((stake, index) => (stake > 0n ? [{ index, stake }] : []))
    : []

  return (
    <div className="prediction-mine">
      <span>내 예측</span>
      {picks.length > 0 ? (
        <ul>
          {picks.map((pick) => (
            <li key={pick.index}>
              <strong>{rangeOf(market, pick.index)}</strong> · {formatPredictionPoint(pick.stake)} 포인트
            </li>
          ))}
        </ul>
      ) : (
        <p>참여가 기록됐어요. 잠시 후 내 예측이 표시됩니다.</p>
      )}
      {phase === 'OPEN' || phase === 'COUNTING' ? (
        <p className="prediction-hint">결과는 판매 집계가 끝난 뒤 발표됩니다.</p>
      ) : null}
      {placed?.surveyFailed && (
        <p className="prediction-error">예측은 기록됐지만 선택한 이유를 저장하지 못했습니다. 다시 참여하지 않아도 됩니다.</p>
      )}
    </div>
  )
}

/**
 * 결과와 받을 것.
 *
 * 결과는 누르고 나서 보인다 — 발표를 여는 순간을 사용자가 고른다. 받을 것이 있으면 결과 바로
 * 아래에서 받는다.
 */
function Outcome({ market, state, phase, onClaimed }: {
  market: PredictionMarket
  state: PredictionChainState | undefined
  phase: MarketPhase
  onClaimed: () => void
}) {
  const [revealed, setRevealed] = useState(false)
  const claim = useMutation({ mutationFn: () => claimPrediction(market), onSuccess: onClaimed })

  const staked = (state?.userStake ?? 0n) > 0n
  const refund = phase === 'CANCELED' || Boolean(state?.refundOnly)
  const payout = state && state.winningPool > 0n
    ? (state.totalPool * state.userWinningStake) / state.winningPool
    : 0n
  const won = (state?.userWinningStake ?? 0n) > 0n
  const canClaim = Boolean(state && staked && !state.claimed && (refund || won))

  if (phase === 'CANCELED') {
    return (
      <div className="prediction-outcome">
        <p>이 예측은 무효가 되었습니다. 건 포인트는 그대로 돌려받을 수 있습니다.</p>
        {canClaim && <Button onClick={() => claim.mutate()} loading={claim.isPending}>{formatPredictionPoint(state!.userStake)} 포인트 돌려받기</Button>}
        {state?.claimed && <p className="prediction-success">돌려받기 완료</p>}
        {claim.error ? <p className="prediction-error">{toErrorMessage(claim.error)}</p> : null}
      </div>
    )
  }

  if (!revealed || market.soldQuantity === null) {
    return <Button variant="secondary" onClick={() => setRevealed(true)}>결과 확인하기</Button>
  }

  const winning = market.winningBucketIndex ?? 0
  return (
    <div className="prediction-outcome">
      <p
        className="prediction-card__result"
        aria-label={`최종 순판매량 ${market.soldQuantity}개, 당첨 구간 ${rangeOf(market, winning)}`}
      >
        최종 순판매량 <strong>{market.soldQuantity.toLocaleString()}</strong>개 · 당첨 구간 {rangeOf(market, winning)}
      </p>
      {staked && state && (
        refund ? (
          <p>맞힌 사람이 없어 건 포인트를 모두 돌려받습니다.</p>
        ) : won ? (
          <p className="prediction-success">
            맞혔어요! {formatPredictionPoint(state.userStake)} 포인트를 걸어 {formatPredictionPoint(payout)} 포인트를 받습니다
            ({payout - state.userStake >= 0n ? '+' : ''}{formatPredictionPoint(payout - state.userStake)}).
          </p>
        ) : (
          <p>아쉽게도 빗나갔어요. 건 {formatPredictionPoint(state.userStake)} 포인트는 맞힌 분들께 돌아갑니다.</p>
        )
      )}
      {canClaim && (
        <Button onClick={() => claim.mutate()} loading={claim.isPending}>
          {refund ? `${formatPredictionPoint(state!.userStake)} 포인트 돌려받기` : `${formatPredictionPoint(payout)} 포인트 받기`}
        </Button>
      )}
      {state?.claimed && <p className="prediction-success">받기 완료</p>}
      {claim.error ? <p className="prediction-error">{toErrorMessage(claim.error)}</p> : null}
    </div>
  )
}
