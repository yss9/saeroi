import { formatPredictionPoint } from '../../services/predictionContractService.ts'
import type { PredictionMarket, PredictionReasonCode } from '../../types/prediction.types.ts'
import type { StatusTone } from '../../../../components/common/index.ts'
import { PREDICTION_REASONS } from '../../utils/predictionReasons.ts'

export const REASON_LABELS = Object.fromEntries(
  PREDICTION_REASONS.map(({ value, label }) => [value, label]),
) as Record<PredictionReasonCode, string>

export const MARKET_STATUS: Record<PredictionMarket['status'], { label: string; tone: StatusTone }> = {
  OPEN: { label: '예측 진행 중', tone: 'info' },
  RESOLVING: { label: '결과 집계 중', tone: 'warning' },
  RESOLVED: { label: '결과 확정', tone: 'success' },
  CANCELED: { label: '시장 취소', tone: 'danger' },
}

export function formatDate(value: string) {
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium' }).format(new Date(value))
}

/** 0~1 비율을 반올림한 퍼센트로. */
export function formatRate(rate: number | null) {
  return rate === null ? '—' : `${Math.round(rate * 100)}%`
}

/** 부호가 붙은 퍼센트. 예측이 실제보다 많으면 +, 적으면 − 다. */
export function formatSignedRate(rate: number | null) {
  if (rate === null) return '—'
  const rounded = Math.round(rate * 100)
  return `${rounded > 0 ? '+' : rounded < 0 ? '−' : '±'}${Math.abs(rounded)}%`
}

export function formatQuantity(value: number | null) {
  return value === null ? '—' : `${Math.round(value).toLocaleString()}개`
}

export function formatPoint(value: string | bigint) {
  return `${formatPredictionPoint(typeof value === 'bigint' ? value : BigInt(value))} PRED`
}

export function share(value: number, total: number) {
  return total === 0 ? 0 : value / total
}
