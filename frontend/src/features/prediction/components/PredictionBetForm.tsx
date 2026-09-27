import { useMutation } from '@tanstack/react-query'
import { formatUnits, parseUnits } from 'ethers'
import { useState, type FormEvent } from 'react'
import { Button } from '../../../components/common/index.ts'
import { toErrorMessage } from '../../../utils/errors.ts'
import { savePredictionSurvey } from '../api/predictionApi.ts'
import {
  approveAndPlacePredictionBet,
  formatPredictionPoint,
  type PredictionBetStep,
} from '../services/predictionContractService.ts'
import type { PredictionChainState, PredictionMarket, PredictionReasonCode } from '../types/prediction.types.ts'
import { bucketShare } from '../utils/marketPhase.ts'
import { PREDICTION_REASONS } from '../utils/predictionReasons.ts'

const QUICK_AMOUNTS = ['10', '50', '100']

const STEP_LABEL: Record<PredictionBetStep, string> = {
  APPROVE: '1/2 포인트 사용 승인 중 — 지갑에서 승인해 주세요',
  PLACE_BET: '2/2 예측 기록 중 — 지갑에서 한 번 더 승인해 주세요',
}

/** 입력한 포인트를 체인 단위로. 숫자가 아니면 null. */
function toUnits(value: string): bigint | null {
  if (!/^\d+(\.\d{1,18})?$/.test(value.trim())) return null
  try { return parseUnits(value.trim(), 18) } catch { return null }
}

/**
 * 예측 참여.
 *
 * **고를 것을 미리 골라 두지 않는다.** 첫 구간과 첫 이유가 골라진 채로 열리면 아무것도 누르지
 * 않고 제출해도 예측이 된다 — 사용자가 고르지 않은 예측이 기록되고, 브랜드 분석에도 섞인다.
 *
 * **시장마다 한 번이다.** 서버가 이유를 시장당 하나만 받는다. 컨트랙트는 여러 번 걸 수 있어서,
 * 두 번째 참여는 포인트만 빠지고 이유 저장이 거절된다. 그래서 참여한 시장에는 이 폼을 내지 않는다.
 */
export function PredictionBetForm({ market, state, balance, onPlaced }: {
  market: PredictionMarket
  state: PredictionChainState | undefined
  balance: bigint | undefined
  /** 체인에 기록됐다. `surveyFailed` 면 이유만 저장하지 못했다 */
  onPlaced: (surveyFailed: boolean) => void
}) {
  const [bucket, setBucket] = useState<number | null>(null)
  const [amount, setAmount] = useState('10')
  const [reasonCode, setReasonCode] = useState<PredictionReasonCode | null>(null)
  const [reasonText, setReasonText] = useState('')
  const [step, setStep] = useState<PredictionBetStep | null>(null)

  const units = toUnits(amount)
  const amountProblem = units === null || units <= 0n
    ? '0보다 큰 숫자로 입력해 주세요.'
    : balance !== undefined && units > balance
      ? `보유 포인트(${formatPredictionPoint(balance)})보다 많이 걸 수 없습니다.`
      : null
  const ready = bucket !== null && reasonCode !== null && amountProblem === null

  const bet = useMutation({
    mutationFn: async (choice: { bucket: number; reasonCode: PredictionReasonCode }) => {
      const result = await approveAndPlacePredictionBet(market, choice.bucket, amount, setStep)
      try {
        await savePredictionSurvey(market.id, {
          bucketIndex: choice.bucket, stakeAmount: result.stakeAmount, reasonCode: choice.reasonCode,
          reasonText, transactionHash: result.transactionHash,
        })
        return { surveyFailed: false }
      } catch {
        // 체인에는 이미 기록됐다. 참여가 실패한 것처럼 말하면 다시 걸어 포인트를 또 잃는다.
        return { surveyFailed: true }
      }
    },
    onSettled: () => setStep(null),
    onSuccess: (result) => onPlaced(result.surveyFailed),
  })

  const total = state?.totalPool ?? 0n
  const selectedPool = bucket === null ? 0n : state?.bucketPools[bucket] ?? 0n
  // 지금 이 구간에 걸고 맞힌다면. 뒤에 들어오는 참여에 따라 달라지므로 "지금 기준" 이다.
  const estimate = ready && units !== null ? ((total + units) * units) / (selectedPool + units) : null
  const selected = bucket === null ? null : market.buckets.find((item) => item.index === bucket)

  function submit(event: FormEvent) {
    event.preventDefault()
    if (ready && bucket !== null && reasonCode !== null && !bet.isPending) bet.mutate({ bucket, reasonCode })
  }

  return (
    <form className="prediction-bet" onSubmit={submit}>
      <fieldset className="prediction-bet__buckets">
        <legend><span>1</span>판매량이 어느 구간일까요?</legend>
        <div role="radiogroup" aria-label="예상 판매량 구간">
          {market.buckets.map((item) => {
            const share = bucketShare(state?.bucketPools[item.index] ?? 0n, total)
            return (
              <label key={item.index} className={bucket === item.index ? 'is-selected' : undefined}>
                <input
                  type="radio"
                  name={`bucket-${market.id}`}
                  checked={bucket === item.index}
                  onChange={() => setBucket(item.index)}
                />
                <strong>{item.lowerBound.toLocaleString()}~{item.upperBound.toLocaleString()}개</strong>
                {/* 다른 참여자가 어디에 걸었는지. 적게 몰린 구간을 맞히면 더 많이 받는다. */}
                <span className="prediction-bet__share" aria-label={`참여 포인트의 ${share}%`}>
                  <i style={{ width: `${share}%` }} />
                </span>
                <small>{share}%</small>
              </label>
            )
          })}
        </div>
      </fieldset>

      <fieldset className="prediction-bet__amount">
        <legend><span>2</span>포인트를 얼마나 걸까요?</legend>
        <div className="prediction-bet__amount-row">
          <input
            aria-label="걸 포인트"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            inputMode="decimal"
          />
          {QUICK_AMOUNTS.map((value) => (
            <button key={value} type="button" onClick={() => setAmount(value)}>{value}</button>
          ))}
          {balance !== undefined && balance > 0n && (
            <button type="button" onClick={() => setAmount(formatUnits(balance, 18).replace(/\.0$/, ''))}>전부</button>
          )}
        </div>
        <p className={amountProblem && amount !== '' ? 'prediction-error' : 'prediction-hint'}>
          {amountProblem && amount !== ''
            ? amountProblem
            : `보유 ${balance === undefined ? '—' : formatPredictionPoint(balance)} 포인트`}
        </p>
      </fieldset>

      <fieldset className="prediction-bet__reason">
        <legend><span>3</span>그렇게 예상한 가장 큰 이유는요?</legend>
        <div role="radiogroup" aria-label="예상한 이유">
          {PREDICTION_REASONS.map((reason) => (
            <label key={reason.value} className={reasonCode === reason.value ? 'is-selected' : undefined}>
              <input
                type="radio"
                name={`reason-${market.id}`}
                checked={reasonCode === reason.value}
                onChange={() => setReasonCode(reason.value)}
              />
              {reason.label}
            </label>
          ))}
        </div>
        <textarea
          aria-label="이유 설명 (선택)"
          value={reasonText}
          onChange={(event) => setReasonText(event.target.value)}
          maxLength={500}
          placeholder="더 적고 싶은 이유가 있다면 알려 주세요. (선택)"
        />
        <p className="prediction-hint">이유는 이름 없이 모아 브랜드의 선호도 분석에만 쓰입니다.</p>
      </fieldset>

      <div className="prediction-bet__summary">
        {selected && units !== null && amountProblem === null ? (
          <p>
            <strong>{selected.lowerBound.toLocaleString()}~{selected.upperBound.toLocaleString()}개</strong>에{' '}
            <strong>{amount} 포인트</strong>를 겁니다.
            {estimate !== null && <> 지금 기준으로 맞히면 약 <strong>{formatPredictionPoint(estimate)} 포인트</strong>를 받습니다.</>}
          </p>
        ) : (
          <p>구간과 이유를 고르면 참여할 수 있습니다.</p>
        )}
        <p className="prediction-hint">
          맞힌 사람끼리 모인 포인트 전체를 건 비율대로 나눠 받습니다. 시장마다 한 번만 참여할 수 있고,
          지갑 서명이 두 번 뜹니다(포인트 사용 승인 → 예측 기록).
        </p>
      </div>

      {step && <p className="prediction-progress" role="status">{STEP_LABEL[step]}</p>}
      {bet.error ? <p className="prediction-error" role="alert">{toErrorMessage(bet.error)}</p> : null}

      <Button type="submit" loading={bet.isPending} disabled={!ready}>예측 참여하기</Button>
    </form>
  )
}
