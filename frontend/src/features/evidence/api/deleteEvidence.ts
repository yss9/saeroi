import { apiClient } from '../../../services/apiClient.ts'
import type { EvidenceFile } from '../types/evidence.types.ts'

/**
 * 증빙 한 건을 지운다. `DELETE /api/evidence/{id}`.
 *
 * 소프트 삭제다 — 행은 남고 `deletedAt` 이 채워진다. 서버가 온체인에 고정된
 * 증빙(`EVIDENCE_ANCHORED_ON_CHAIN`, 409)과 업로더·소재 등록자가 아닌 요청
 * (`EVIDENCE_DELETE_FORBIDDEN`, 403)을 막아 준다 — 클라이언트는 그 판정을
 * 다시 하지 않고 서버 응답을 그대로 보여준다.
 */
export async function deleteEvidence(evidenceFileId: number): Promise<EvidenceFile> {
  const { data } = await apiClient.delete<EvidenceFile>(`/evidence/${evidenceFileId}`)
  return data
}
