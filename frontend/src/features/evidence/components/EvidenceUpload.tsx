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
 * <h2>목록은 보는 사람에 따라 범위가 다르다 (S15P21E104-286 §6-1)</h2>
 *
 * <p>`GET /api/evidence` 는 업로더·소재 등록자·현재 보유자에게만 파일 목록을 주고,
 * 그 외 인증 사용자에게는 <b>종류별 개수만</b> 준다. 그래서 이 화면은 세 상태를
 * <b>서로 다르게</b> 그린다.
 *
 * <ul>
 *   <li>`summary` 가 빈 배열 → 정말 증빙이 없다 → EmptyState ("올려 주세요")</li>
 *   <li>`visibility: 'SUMMARY'` → 있지만 가려졌다 → 요약 줄 + "관계자에게만 공개" 안내</li>
 *   <li>`visibility: 'FULL'` → 요약 줄 + 파일 목록</li>
 * </ul>
 *
 * <p><b>가려진 것을 "증빙 없음" 으로 그리지 않는다.</b> 둘을 같은 문구로 말하면 남의
 * 소재를 보는 사람은 증빙이 안 올라왔다고 오해하고, 자기 소재를 보는 사람은 방문자에게
 * 무엇이 보이는지 알 수 없다.
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
      ) : list.data.summary.length === 0 ? (
        // 정말 0건이다. 서버는 가려진 응답에도 `summary` 를 채워 주므로, 빈 요약은
        // "증빙이 없다" 는 뜻이 확실하다. EmptyState 는 여기서만 쓴다.
        <EmptyState
          title="등록된 증빙이 없습니다"
          description={
            readOnly ? '아직 증빙이 올라오지 않았습니다.' : '사진이나 계근표를 올려 주세요.'
          }
        />
      ) : (
        <>
          {/*
            종류별 건수는 FULL·SUMMARY 두 경우 모두 서버가 준다. files 를 세어 만들지
            않는다 — 요약 규칙이 두 곳에 생기고, 순서도 서버가 정한 EvidenceType 열거
            순서를 그대로 써야 화면마다 달라 보이지 않는다.
          */}
          <ul className="evidence__summary" aria-label="증빙 요약">
            {list.data.summary.map(({ evidenceType: type, count }) => (
              <li key={type}>
                {EVIDENCE_TYPE_LABEL[type]} {count}건
              </li>
            ))}
          </ul>

          {/*
            `files` 유무가 아니라 visibility 로 갈라야 한다. 가려진 응답에는 files 키가
            아예 없는데 그것을 빈 배열처럼 다루면 "증빙 없음" 과 구분되지 않는다.
            서버가 visibility 를 응답에 싣는 이유가 바로 그 구분이다.
          */}
          {list.data.visibility === 'FULL' ? (
            <ul className="evidence__list" aria-label="등록된 증빙">
              {(list.data.files ?? []).map((file) => (
                <li key={file.evidenceFileId}>
                  <p>{EVIDENCE_TYPE_LABEL[file.evidenceType]}</p>
                  {/*
                    파일 이름(storageKey)이 아니라 해시를 보여준다. storageKey 는 URL 이
                    아니라 저장소 경로라 <img src> 에 넣으면 깨진 이미지가 되고, 해시는
                    온체인에 올라간 값이라 사용자가 대조할 수 있다.

                    파일을 되돌려주는 엔드포인트는 이제 있다 (contentPath →
                    GET /api/evidence/{id}/content). 다만 미리보기 UI 는 별도 작업이라
                    이 브랜치에서 만들지 않는다 — 그래서 아직 "준비 중" 이라고 말한다.
                  */}
                  <p className="evidence__hash">{file.fileHash}</p>
                  <p>
                    <time dateTime={file.uploadedAt}>{formatDateTime(file.uploadedAt)}</time>
                    {' · 미리보기 준비 중'}
                  </p>
                </li>
              ))}
            </ul>
          ) : (
            // 가려진 것은 없는 것이 아니다. 개수는 위 요약에 이미 나와 있으니 여기서는
            // 무엇이 왜 안 보이는지만 말한다. EmptyState 를 쓰면 "증빙 없음" 이 된다.
            <p className="evidence__masked">
              증빙이 등록되어 있지만 파일 정보는 보이지 않습니다. 파일 이름·해시·업로더는
              업로더와 소재 등록자, 현재 보유자에게만 공개됩니다.
            </p>
          )}
        </>
      )}
    </div>
  )
}

function formatDateTime(value: string): string {
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return parsed.toLocaleString('ko-KR', { dateStyle: 'medium', timeStyle: 'short' })
}
