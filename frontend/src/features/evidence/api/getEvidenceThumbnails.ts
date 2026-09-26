import { apiClient } from '../../../services/apiClient.ts'
import type { EvidenceReferenceType, EvidenceThumbnail } from '../types/evidence.types.ts'

/**
 * 여러 대상의 대표 사진을 한 번에 찾는다. `GET /api/evidence/thumbnails`.
 *
 * 마켓플레이스 카드 여러 장을 한 화면에 그릴 때, 카드마다 따로 부르면 N+1 이 된다 —
 * 목록 조회 한 번으로 모은 `materialId` 를 여기 한 번에 넘긴다.
 *
 * 응답은 **키가 있는 대상만** 들어 있는 맵이다 (JSON 이라 키는 문자열이지만 값은
 * 숫자 `referenceId` 다). 사진이 없는 대상은 키 자체가 없다 — `undefined` 로 읽히므로
 * 화면은 "이 소재는 사진이 없다" 와 "아직 안 불러왔다" 를 자연히 구분할 수 있다.
 */
export async function getEvidenceThumbnails(
  referenceType: EvidenceReferenceType,
  referenceIds: readonly number[],
): Promise<Record<number, EvidenceThumbnail>> {
  // 배열을 그대로 넘기면 axios 가 `referenceIds[]=1&referenceIds[]=2` 로 직렬화해
  // Spring 의 `List<Long>` 바인딩과 어긋난다. 다른 검색 API(`processingRequestApi.ts`)와
  // 같은 관례로 콤마 문자열로 합친다 — Spring 의 기본 컨버터가 콤마 구분 문자열을
  // 컬렉션으로 바꿔 준다.
  const { data } = await apiClient.get<Record<number, EvidenceThumbnail>>(
    '/evidence/thumbnails',
    { params: { referenceType, referenceIds: referenceIds.join(',') } },
  )
  return data
}
