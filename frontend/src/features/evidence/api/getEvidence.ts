import { apiClient } from '../../../services/apiClient.ts'
import type { EvidenceListResponse, EvidenceReferenceType } from '../types/evidence.types.ts'

/**
 * 한 대상의 증빙 목록.
 *
 * ⚠️ **응답은 배열이 아니라 객체다** (S15P21E104-286 §6-1, BE `!192`). 보는 사람의
 * 관계에 따라 서버가 범위를 갈라 주고, 어느 범위인지를 `visibility` 로 말해 준다.
 * 예전 계약(`EvidenceFile[]`)을 그대로 쓰면 `data.length` · `data.map` 이 객체에
 * 돌아 화면이 깨진다.
 *
 * `files` 안의 순서는 **업로드 순서(오래된 것 먼저)** 로 서버가 정렬해 준다.
 * 클라이언트에서 다시 정렬하지 않는다 — 증빙은 "작업 전 → 작업 후" 처럼 시간 순서가
 * 뜻을 갖는 자료이고, 양쪽에서 정렬하면 같은 소재의 사진 순서가 화면마다 달라 보인다.
 *
 * 증빙이 없으면 `summary` 가 빈 배열이다. 대상이 없어도 빈 배열이다 — 목록을 묻는
 * 요청이라 그게 정확한 답이고, 대상 존재는 업로드할 때 확인된다.
 *
 * **비회원은 401 이다.** 소재 상세는 공개 라우트인데 이 엔드포인트는 인증을 요구한다.
 * 비회원에게 요약을 여는 것은 QR 공개 조회(§6-3)에 걸려 있는 별도 결정이므로 여기서
 * 우회하지 않는다. 그동안 이 섹션은 조회 실패로 그려진다.
 */
export async function getEvidence(
  referenceType: EvidenceReferenceType,
  referenceId: number,
): Promise<EvidenceListResponse> {
  const { data } = await apiClient.get<EvidenceListResponse>('/evidence', {
    params: { referenceType, referenceId },
  })
  return data
}
