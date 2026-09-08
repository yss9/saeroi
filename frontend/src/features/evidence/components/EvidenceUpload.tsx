import { useState } from 'react'
import {
  Button,
  EmptyState,
  ErrorMessage,
  FileUploader,
  Loading,
  Select,
} from '../../../components/common/index.ts'
import { useEvidence } from '../hooks/useEvidence.ts'
import { useUploadEvidence } from '../hooks/useUploadEvidence.ts'
import {
  EVIDENCE_ACCEPT,
  EVIDENCE_MAX_FILES,
  EVIDENCE_MAX_FILE_SIZE,
  EVIDENCE_TYPE_LABEL,
  MATERIAL_EVIDENCE_TYPES,
  type EvidenceReferenceType,
  type EvidenceType,
} from '../types/evidence.types.ts'

export interface EvidenceUploadProps {
  referenceType: EvidenceReferenceType
  referenceId: number | null
  /** 업로드 UI 를 감추고 목록만 보여준다. 남의 소재를 볼 때 쓴다 */
  readOnly?: boolean
}

/**
 * 증빙 업로드 + 목록. S15P21E104-165.
 *
 * <h2>증빙은 지울 수 없다</h2>
 *
 * <p>파일 해시가 온체인에 기록되므로 잘못 올린 것을 되돌릴 방법이 없다. 그래서 이 화면은
 * <b>올리기 전에 무엇이 올라가는지 보여주고, 올린 뒤에는 지우기를 제안하지 않는다.</b>
 * 삭제 버튼을 두면 사용자는 되돌릴 수 있다고 믿는다.
 *
 * <h2>서버가 막는 것을 여기서도 막는다</h2>
 *
 * <p>중복이 아니다. 서버 거절은 요청을 보낸 뒤에 오고, 사용자는 그때까지 파일이
 * 올라가는 중이라고 생각한다. 앞에서 막으면 <b>고르는 순간</b> 이유를 알 수 있다.
 * 그리고 목록이 서버와 어긋나면 조용히 실패하므로 값을 한곳
 * ({@code evidence.types.ts})에서만 정의한다.
 */
export function EvidenceUpload({ referenceType, referenceId, readOnly }: EvidenceUploadProps) {
  const [evidenceType, setEvidenceType] = useState<EvidenceType>('BEFORE_PHOTO')
  const [selected, setSelected] = useState<File[]>([])
  const [tooMany, setTooMany] = useState(false)

  const list = useEvidence(referenceType, referenceId)
  const upload = useUploadEvidence()

  if (referenceId === null) return <ErrorMessage error="잘못된 주소입니다." />

  const submit = () => {
    if (selected.length === 0) return
    upload.mutate(
      { referenceType, referenceId, evidenceType, files: selected },
      {
        // 성공했을 때만 선택을 비운다. 실패한 채로 비우면 사용자가 파일을 다시
        // 골라야 하는데, 무엇을 골랐는지는 이미 화면에서 사라진 상태다.
        onSuccess: () => setSelected([]),
      },
    )
  }

  return (
    <div className="evidence">
      {!readOnly && (
        <div className="evidence__form">
          <Select
            label="증빙 종류"
            options={MATERIAL_EVIDENCE_TYPES.map((value) => ({
              value,
              label: EVIDENCE_TYPE_LABEL[value],
            }))}
            value={evidenceType}
            onChange={(event) => setEvidenceType(event.target.value as EvidenceType)}
          />

          <FileUploader
            label={`파일 선택 (최대 ${EVIDENCE_MAX_FILES}개, 개당 10MB)`}
            accept={EVIDENCE_ACCEPT}
            multiple
            maxSizeBytes={EVIDENCE_MAX_FILE_SIZE}
            disabled={upload.isPending}
            onChange={(files) => {
              // 개수 초과를 조용히 잘라내지 않는다. 잘라내면 사용자는 10개를 골랐는데
              // 몇 개가 왜 빠졌는지 모른다.
              setTooMany(files.length > EVIDENCE_MAX_FILES)
              setSelected(files.length > EVIDENCE_MAX_FILES ? [] : files)
            }}
          />

          {tooMany && (
            <p role="alert">
              한 번에 {EVIDENCE_MAX_FILES}개까지 올릴 수 있습니다. 나눠서 올려 주세요.
            </p>
          )}

          {selected.length > 0 && (
            <>
              {/*
                올리기 전에 목록을 보여준다. 증빙은 되돌릴 수 없으므로 무엇이 올라가는지
                확인할 기회가 있어야 한다.
              */}
              <ul aria-label="올릴 파일">
                {selected.map((file) => (
                  <li key={`${file.name}-${file.size}`}>{file.name}</li>
                ))}
              </ul>
              <Button type="button" onClick={submit} loading={upload.isPending}>
                증빙 {selected.length}개 올리기
              </Button>
            </>
          )}

          {upload.isError && <ErrorMessage error={upload.error} />}
        </div>
      )}

      {list.isPending ? (
        <Loading variant="skeleton" rows={2} label="증빙을 불러오는 중" />
      ) : list.isError ? (
        <ErrorMessage error={list.error} onRetry={() => void list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState
          title="등록된 증빙이 없습니다"
          description={
            readOnly ? '아직 증빙이 올라오지 않았습니다.' : '사진이나 계근표를 올려 주세요.'
          }
        />
      ) : (
        <ul className="evidence__list" aria-label="등록된 증빙">
          {list.data.map((file) => (
            <li key={file.evidenceFileId}>
              <p>{EVIDENCE_TYPE_LABEL[file.evidenceType]}</p>
              {/*
                파일을 되돌려주는 엔드포인트가 아직 없다. storageKey 를 <img src> 에
                넣으면 깨진 이미지가 되므로, 파일 이름 대신 해시를 보여주고 미리보기가
                준비 중임을 밝힌다. 사용자는 자기 증빙이 온체인에 올라간 그것인지
                이 해시로 대조할 수 있다.
              */}
              <p className="evidence__hash">{file.fileHash}</p>
              <p>
                <time dateTime={file.uploadedAt}>{formatDateTime(file.uploadedAt)}</time>
                {' · 미리보기 준비 중'}
              </p>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function formatDateTime(value: string): string {
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return parsed.toLocaleString('ko-KR', { dateStyle: 'medium', timeStyle: 'short' })
}
