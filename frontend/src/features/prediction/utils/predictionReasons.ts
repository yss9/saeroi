import type { PredictionReasonCode } from '../types/prediction.types.ts'

/** 예측 이유. 소비자가 고르고, 브랜드 분석이 같은 이름으로 모은다. */
export const PREDICTION_REASONS: readonly { value: PredictionReasonCode; label: string }[] = [
  { value: 'DESIGN', label: '디자인' }, { value: 'PRICE', label: '가격' },
  { value: 'BRAND', label: '브랜드' }, { value: 'SUSTAINABILITY', label: '친환경 가치' },
  { value: 'PRACTICALITY', label: '실용성' }, { value: 'TREND', label: '트렌드' },
  { value: 'OTHER', label: '기타' },
]
