import { useQuery } from '@tanstack/react-query'
import { getEvidence } from '../api/getEvidence.ts'
import { evidenceKeys } from '../api/evidenceKeys.ts'
import type { EvidenceReferenceType } from '../types/evidence.types.ts'

/**
 * 한 대상의 증빙 목록.
 *
 * `referenceId` 가 `null` 이면 조회하지 않는다 — 라우트 파라미터가 아직 없거나
 * 잘못된 상태에서 서버에 의미 없는 요청이 가는 것을 막는다.
 */
export function useEvidence(
  referenceType: EvidenceReferenceType,
  referenceId: number | null,
) {
  return useQuery({
    queryKey: evidenceKeys.list(referenceType, referenceId ?? 0),
    queryFn: () => getEvidence(referenceType, referenceId as number),
    enabled: referenceId !== null,
  })
}
