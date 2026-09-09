import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type { AppError } from '../../../utils/errors.ts'
import { evidenceKeys } from '../api/evidenceKeys.ts'
import { uploadEvidence, type UploadEvidenceParams } from '../api/uploadEvidence.ts'
import type { EvidenceFile } from '../types/evidence.types.ts'

/**
 * 증빙 업로드.
 *
 * 성공 후 그 대상의 증빙 목록을 무효화한다. 빠뜨리면 올렸는데 목록에 나타나지 않고,
 * 사용자는 업로드가 실패한 줄 알고 다시 올린다 — <b>증빙은 지울 수 없는 기록이라</b>
 * 중복 업로드가 그대로 남는다.
 *
 * 응답으로 캐시를 직접 채우지 않는다. 이 요청이 방금 올린 것만 돌려주므로 그것으로
 * 목록을 덮으면 이전에 올린 증빙이 화면에서 사라진다.
 *
 * <h2>진행률은 mutation 상태가 아니다</h2>
 *
 * <p>TanStack Query 에 진행률 자리가 없으므로 `useState` 로 따로 들고 있다. 이 훅을 쓰는
 * 컴포넌트 하나에만 필요한 값이라 전역 스토어로 올리지 않는다 (ARCHITECTURE.md §3).
 *
 * <p><b>`null` 은 "모른다" 다.</b> 0 과 구분해야 한다 — 0 은 "아직 한 바이트도 안 갔다" 이고
 * `null` 은 브라우저가 총 크기를 주지 않아 계산할 수 없다는 뜻이다. 둘을 섞으면 진행률을
 * 알 수 없는 요청이 영원히 0% 로 보인다.
 */
export function useUploadEvidence() {
  const queryClient = useQueryClient()
  const [progress, setProgress] = useState<number | null>(null)

  const mutation = useMutation<EvidenceFile[], AppError, UploadEvidenceParams>({
    mutationFn: (params) => uploadEvidence({ ...params, onProgress: setProgress }),
    // 성공이든 실패든 진행률을 되돌린다. 남겨 두면 다음 업로드가 지난번 값에서
    // 시작하는 것처럼 보인다.
    onSettled: () => setProgress(null),
    onSuccess: (_uploaded, params) => {
      void queryClient.invalidateQueries({
        queryKey: evidenceKeys.list(params.referenceType, params.referenceId),
      })
    },
  })

  return { ...mutation, progress }
}
