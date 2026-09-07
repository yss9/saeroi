import { apiClient } from '../../../services/apiClient.ts'
import type { EvidenceFile, EvidenceReferenceType } from '../types/evidence.types.ts'

/**
 * 한 대상의 증빙 목록.
 *
 * 서버가 **업로드 순서(오래된 것 먼저)** 로 정렬해 준다. 클라이언트에서 다시 정렬하지
 * 않는다 — 증빙은 "작업 전 → 작업 후" 처럼 시간 순서가 뜻을 갖는 자료이고, 양쪽에서
 * 정렬하면 같은 소재의 사진 순서가 화면마다 달라 보인다.
 *
 * 증빙이 없으면 빈 배열이다. 대상이 없어도 빈 배열이다 — 목록을 묻는 요청이라
 * 그게 정확한 답이고, 대상 존재는 업로드할 때 확인된다.
 */
export async function getEvidence(
  referenceType: EvidenceReferenceType,
  referenceId: number,
): Promise<EvidenceFile[]> {
  const { data } = await apiClient.get<EvidenceFile[]>('/evidence', {
    params: { referenceType, referenceId },
  })
  return data
}
