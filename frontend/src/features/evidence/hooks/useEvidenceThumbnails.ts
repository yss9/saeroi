import { useQuery } from '@tanstack/react-query'
import { evidenceKeys } from '../api/evidenceKeys.ts'
import { getEvidenceThumbnails } from '../api/getEvidenceThumbnails.ts'
import type { EvidenceReferenceType } from '../types/evidence.types.ts'

/**
 * 여러 대상의 대표 사진을 한 번에 가져온다. 마켓플레이스 카드 목록·소재 상세의
 * 판매 정보 탭이 쓴다 — 카드마다 따로 부르지 않고 화면이 보여줄 `materialId` 를
 * 모아 한 번에 요청한다.
 *
 * `referenceIds` 가 비어 있으면 요청하지 않는다 — 목록이 아직 로딩 중이거나 빈
 * 화면일 때 빈 배열로 조회해 서버에 헛 요청을 보내지 않는다.
 */
export function useEvidenceThumbnails(
  referenceType: EvidenceReferenceType,
  referenceIds: readonly number[],
) {
  return useQuery({
    queryKey: evidenceKeys.thumbnails(referenceType, referenceIds),
    queryFn: () => getEvidenceThumbnails(referenceType, referenceIds),
    enabled: referenceIds.length > 0,
    // 대표 사진 배정은 자주 바뀌지 않는다 (같은 소재에 새 사진을 올려도 "가장 먼저
    // 올라온 사진"이 그대로다). 목록을 오가며 다시 받을 이유가 없다.
    staleTime: 5 * 60 * 1000,
  })
}
