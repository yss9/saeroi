import { apiClient } from '../../../services/apiClient.ts'
import type {
  EvidenceFile,
  EvidenceReferenceType,
  EvidenceType,
} from '../types/evidence.types.ts'

export interface UploadEvidenceParams {
  referenceType: EvidenceReferenceType
  referenceId: number
  evidenceType: EvidenceType
  files: File[]
}

/**
 * 증빙 업로드.
 *
 * <b>`multipart/form-data` 다.</b> 메타데이터와 파일을 한 요청에 담는다 — 두 요청으로
 * 나누면 파일은 올라갔는데 메타데이터가 실패한 상태가 생기고, 그 파일은 아무도
 * 가리키지 않는 고아가 된다.
 *
 * <b>`Content-Type` 을 직접 지정하지 않는다.</b> `apiClient` 의 기본값이
 * `application/json` 인데, `FormData` 를 보낼 때는 브라우저가 `boundary` 를 포함한
 * 헤더를 만들어야 한다. 직접 `multipart/form-data` 를 적으면 boundary 가 빠져
 * 서버가 파트를 하나도 못 읽는다. `undefined` 로 지워 브라우저에 맡긴다.
 *
 * 파일 파트 이름은 모두 `files` 다 — 서버가 `@RequestParam("files") List<MultipartFile>`
 * 로 받으므로 이름이 다르면 파일이 없는 요청으로 보인다.
 */
export async function uploadEvidence({
  referenceType,
  referenceId,
  evidenceType,
  files,
}: UploadEvidenceParams): Promise<EvidenceFile[]> {
  const form = new FormData()
  form.append('referenceType', referenceType)
  form.append('referenceId', String(referenceId))
  form.append('evidenceType', evidenceType)
  files.forEach((file) => form.append('files', file))

  const { data } = await apiClient.post<EvidenceFile[]>('/evidence', form, {
    headers: { 'Content-Type': undefined },
  })
  return data
}
