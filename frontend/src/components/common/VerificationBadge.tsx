import { StatusBadge, type StatusTone } from './StatusBadge.tsx'

/**
 * 소재 검증 단계. Backend `material.verification_state` ENUM 과 문자열이 일치한다.
 * 단계가 올라갈수록 이력이 더 많이 확인된 소재다.
 */
export type VerificationState =
  | 'REGISTERED'
  | 'SOURCE_VERIFIED'
  | 'TRANSACTION_VERIFIED'
  | 'PROCESSING_VERIFIED'
  | 'PRODUCT_LINKED'

const LABEL: Record<VerificationState, string> = {
  REGISTERED: '등록됨',
  SOURCE_VERIFIED: '출처 검증',
  TRANSACTION_VERIFIED: '거래 검증',
  PROCESSING_VERIFIED: '가공 검증',
  PRODUCT_LINKED: '제품 연결',
}

const TONE: Record<VerificationState, StatusTone> = {
  REGISTERED: 'neutral',
  SOURCE_VERIFIED: 'info',
  TRANSACTION_VERIFIED: 'info',
  PROCESSING_VERIFIED: 'success',
  PRODUCT_LINKED: 'success',
}

export interface VerificationBadgeProps {
  state: VerificationState
}

/**
 * QR Passport 와 Material 상세 양쪽에서 쓰기 때문에 공통에 둔다.
 * (한 도메인에서만 쓰였다면 features/material/components 가 맞다.)
 */
export function VerificationBadge({ state }: VerificationBadgeProps) {
  return <StatusBadge tone={TONE[state]}>{LABEL[state]}</StatusBadge>
}
