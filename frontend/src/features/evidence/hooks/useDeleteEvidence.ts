import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { evidenceKeys } from '../api/evidenceKeys.ts'
import { deleteEvidence } from '../api/deleteEvidence.ts'
import type { EvidenceFile, EvidenceReferenceType } from '../types/evidence.types.ts'

export interface DeleteEvidenceParams {
  evidenceFileId: number
  referenceType: EvidenceReferenceType
  referenceId: number
}

/**
 * 증빙 삭제.
 *
 * 성공하면 그 대상의 증빙 목록을 무효화한다 — 응답으로 캐시를 직접 고치지 않는 이유는
 * `useUploadEvidence` 와 같다. 목록은 `summary`(종류별 개수)와 `files` 를 같이 들고 있고,
 * 하나가 지워지면 둘 다 바뀌어야 하는데 그 계산을 클라이언트가 다시 하면 서버 집계와
 * 어긋날 수 있다.
 */
export function useDeleteEvidence() {
  const queryClient = useQueryClient()

  return useMutation<EvidenceFile, AppError, DeleteEvidenceParams>({
    mutationFn: ({ evidenceFileId }) => deleteEvidence(evidenceFileId),
    onSuccess: (_deleted, { referenceType, referenceId }) => {
      void queryClient.invalidateQueries({
        queryKey: evidenceKeys.list(referenceType, referenceId),
      })
    },
  })
}
