import { useQuery } from '@tanstack/react-query'
import { evidenceKeys } from '../api/evidenceKeys.ts'
import { getEvidenceContent } from '../api/getEvidenceContent.ts'

/**
 * 증빙 파일 하나의 바이트.
 *
 * <b>`enabled` 를 기본으로 끄고 호출부가 켠다.</b> 증빙은 한 소재에 최대 10개까지
 * 붙고 파일당 10MB 라, 목록을 그리는 것만으로 전부 내려받으면 최악의 경우 100MB 가
 * 조용히 흐른다. 사용자가 볼 것을 고른 뒤에 받는다.
 *
 * 캐시 키는 `evidenceKeys.content(id)` 다 — 목록 키와 분리되어 있어야
 * 목록 무효화가 이미 받아 둔 바이트까지 버리지 않는다. 같은 파일은 두 번 받지 않는다.
 */
export function useEvidenceContent(
  evidenceFileId: number,
  contentPath: string,
  enabled: boolean,
) {
  return useQuery({
    queryKey: evidenceKeys.content(evidenceFileId),
    queryFn: () => getEvidenceContent(contentPath),
    enabled,
    // 파일 바이트는 바뀌지 않는다 — 저장 이름이 UUID 이고 증빙은 덮어쓰기가 없다.
    // 다시 받을 이유가 없으므로 만료시키지 않는다.
    staleTime: Infinity,
    // 인가 실패(403)는 다시 시도해도 같다. 사용자를 기다리게 하지 않는다.
    retry: false,
  })
}
