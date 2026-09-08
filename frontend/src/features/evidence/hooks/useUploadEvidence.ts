import { useMutation, useQueryClient } from '@tanstack/react-query'
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
 */
export function useUploadEvidence() {
  const queryClient = useQueryClient()

  return useMutation<EvidenceFile[], AppError, UploadEvidenceParams>({
    mutationFn: uploadEvidence,
    onSuccess: (_uploaded, params) => {
      void queryClient.invalidateQueries({
        queryKey: evidenceKeys.list(params.referenceType, params.referenceId),
      })
    },
  })
}
