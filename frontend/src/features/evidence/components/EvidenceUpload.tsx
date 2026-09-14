import { useState } from 'react'
import {
  Button,
  ConfirmModal,
  EmptyState,
  ErrorMessage,
  FileUploader,
  LocalFilePreview,
  Loading,
  Select,
} from '../../../components/common/index.ts'
import { useDeleteEvidence } from '../hooks/useDeleteEvidence.ts'
import { useEvidence } from '../hooks/useEvidence.ts'
import { useUploadEvidence } from '../hooks/useUploadEvidence.ts'
import {
  EVIDENCE_ACCEPT,
  EVIDENCE_MAX_FILES,
  EVIDENCE_MAX_FILE_SIZE,
  EVIDENCE_TYPE_LABEL,
  MATERIAL_EVIDENCE_TYPES,
  type EvidenceFile,
  type EvidenceReferenceType,
  type EvidenceType,
} from '../types/evidence.types.ts'
import { EvidencePreview } from './EvidencePreview.tsx'
import './evidence.css'

export interface EvidenceUploadProps {
  referenceType: EvidenceReferenceType
  referenceId: number | null
  /** 업로드 UI 를 감추고 목록만 보여준다. 남의 소재를 볼 때 쓴다 */
  readOnly?: boolean
}

/**
 * 증빙 업로드 + 목록. S15P21E104-165.
 *
 * <h2>삭제는 소프트 삭제이고, 판정은 서버가 한다</h2>
 *
 * <p>`DELETE /api/evidence/{id}` 는 행을 지우지 않고 `deletedAt` 만 채운다 — `fileHash` 가
 * 나중에 온체인 manifest 에서 발견되면 무엇의 해시인지 알 수 있어야 하기 때문이다
 * ({@code EvidenceDeletionService} 주석). 그래서 <b>이 화면은 누가 지울 수 있는지 다시
 * 판단하지 않는다</b> — 업로더·소재 등록자가 아니면 403(`EVIDENCE_DELETE_FORBIDDEN`),
 * 이미 온체인에 고정됐으면 409(`EVIDENCE_ANCHORED_ON_CHAIN`)를 서버가 돌려주고, 화면은
 * 그 에러 메시지를 그대로 보여준다.
 *
 * <p>올리기 전에 무엇이 올라가는지 보여주는 이유는 여전히 유효하다 — 지울 수 있는 경우도
 * 있지만 <b>항상 지울 수 있는 것은 아니므로</b>, 잘못 고른 파일을 업로드 전에 걸러내는
 * 편이 여전히 최선이다.
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
 *
 * <p><b>검증 자체는 이 컴포넌트가 하지 않는다</b> — 형식·크기·개수와 거절 문구는
 * 공통 {@code FileUploader} 가 맡는다 (S15P21E104-253). 도메인이 하는 일은 정책 값을
 * 넘기는 것뿐이다. 여기서 다시 세면 같은 규칙이 두 곳에 생긴다.
 *
 * <h2>미리보기는 두 번 나온다</h2>
 *
 * <ul>
 *   <li><b>올리기 전</b> — 고른 파일을 브라우저가 가진 바이트로 그린다
 *       ({@code LocalFilePreview}). 증빙은 되돌릴 수 없으니 무엇이 올라가는지 눈으로
 *       확인할 기회가 있어야 한다</li>
 *   <li><b>올린 뒤</b> — 서버 파일을 {@code EvidencePreview} 가 그린다. 눌러야 받는다
 *       (파일 10개 × 10MB 를 목록만 그리면서 내려받지 않는다)</li>
 * </ul>
 *
 * <p>둘 다 {@code <img src={contentPath}>} 가 아니다 — 조회 경로가 Authorization 헤더를
 * 요구해서 브라우저가 스스로 보내는 요청은 401 을 받는다
 * ({@code api/getEvidenceContent.ts} 주석).
 */
export function EvidenceUpload({ referenceType, referenceId, readOnly }: EvidenceUploadProps) {
  const [evidenceType, setEvidenceType] = useState<EvidenceType>('BEFORE_PHOTO')
  const [selected, setSelected] = useState<File[]>([])
  // 지우려는 파일 하나만 든다 — 확인 모달은 한 번에 하나만 뜬다.
  const [pendingDelete, setPendingDelete] = useState<EvidenceFile | null>(null)

  const list = useEvidence(referenceType, referenceId)
  const upload = useUploadEvidence()
  const remove = useDeleteEvidence()

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
          {/* 종류 선택과 파일 선택은 한 번에 정하는 짝이라 같은 행에 좌우로 둔다. */}
          <div className="evidence__form-row">
            <Select
              label="증빙 종류"
              options={MATERIAL_EVIDENCE_TYPES.map((value) => ({
                value,
                label: EVIDENCE_TYPE_LABEL[value],
              }))}
              value={evidenceType}
              onChange={(event) => setEvidenceType(event.target.value as EvidenceType)}
            />

            {/*
              형식·크기·개수 검증과 거절 문구는 모두 공통 컴포넌트가 한다 (S15P21E104-253).
              여기서 다시 세면 같은 규칙이 두 곳에 생기고, 상한을 고칠 때 한쪽이 남는다.
              도메인이 하는 일은 **정책 값을 넘기는 것**뿐이다.
            */}
            <FileUploader
              label="파일 선택"
              hint={`사진(jpg·png·webp)과 pdf 를 최대 ${EVIDENCE_MAX_FILES}개, 개당 10MB 까지 올릴 수 있습니다.`}
              accept={EVIDENCE_ACCEPT}
              multiple
              maxSizeBytes={EVIDENCE_MAX_FILE_SIZE}
              maxFiles={EVIDENCE_MAX_FILES}
              uploading={upload.isPending}
              progress={upload.progress}
              onChange={setSelected}
            />
          </div>

          {selected.length > 0 && (
            <>
              {/*
                올리기 전에 목록과 미리보기를 보여준다. 증빙은 되돌릴 수 없으므로 무엇이
                올라가는지 **눈으로** 확인할 기회가 있어야 한다 — 파일명만으로는 사진
                열 장 중 어느 것을 잘못 골랐는지 알 수 없다.

                미리보기는 서버를 거치지 않는다. 아직 올라가지 않았고, 브라우저는 이미
                바이트를 갖고 있다.
              */}
              <ul className="evidence__selected" aria-label="올릴 파일">
                {selected.map((file) => (
                  <li key={`${file.name}-${file.size}`}>
                    <LocalFilePreview file={file} />
                    <span>{file.name}</span>
                  </li>
                ))}
              </ul>
              <Button type="button" onClick={submit} loading={upload.isPending}>
                증빙 {selected.length}개 올리기
              </Button>
            </>
          )}

          {/*
            업로드 결과.

            <b>서버는 일부만 성공시키지 않는다.</b> `EvidenceUploadService` 는 검증을 모두
            끝낸 뒤에 첫 바이트를 쓰고, 중간에 실패하면 이미 쓴 파일을 지우고 트랜잭션을
            되돌린다. 그래서 결과는 "전부 올라갔다" 또는 "하나도 안 올라갔다" 뿐이고,
            **파일별 성공/실패 표를 만들 수 없다** — 만들면 서버가 하지 않는 약속을
            화면이 하는 것이 된다.

            대신 성공한 건에 대해 **무엇이 올라갔는지**를 파일별로 밝힌다. 증빙은 지울 수
            없으므로 사용자가 방금 남긴 기록을 확인할 수 있어야 하고, 해시는 온체인에
            올라가는 값이라 대조에 쓰인다.
          */}
          {upload.isSuccess && upload.data.length > 0 && (
            <div className="evidence__result" role="status">
              <p>증빙 {upload.data.length}개를 올렸습니다.</p>
              <ul aria-label="업로드 결과">
                {upload.data.map((file) => (
                  <li key={file.evidenceFileId}>
                    {EVIDENCE_TYPE_LABEL[file.evidenceType]}
                    <span className="evidence__hash">{file.fileHash}</span>
                  </li>
                ))}
              </ul>
            </div>
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
                  */}
                  <p className="evidence__hash">{file.fileHash}</p>
                  <p>
                    <time dateTime={file.uploadedAt}>{formatDateTime(file.uploadedAt)}</time>
                  </p>
                  {/*
                    미리보기는 여기까지 온 파일에만 붙는다. visibility 가 FULL 이 아니면
                    서버가 evidenceFileId · contentPath 를 아예 주지 않으므로 이 분기에
                    들어올 수 없다.
                  */}
                  <EvidencePreview file={file} />
                  {/*
                    누가 지울 수 있는지는 서버가 판정한다 (업로더·소재 등록자만, 온체인
                    고정 전까지). 여기서 다시 가리면 보유자에게는 안 보였다가 서버 정책이
                    바뀌면 화면이 따라가지 못한다 — 버튼은 항상 보여주고 거절은 에러
                    메시지로 알린다.
                  */}
                  {!readOnly && (
                    <Button
                      type="button"
                      variant="secondary"
                      size="sm"
                      onClick={() => setPendingDelete(file)}
                    >
                      삭제
                    </Button>
                  )}
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

      {remove.isError && <ErrorMessage error={remove.error} />}

      {/*
        열려 있을 때만 마운트한다. `<dialog>` 는 항상 DOM 에 있으면 닫혀 있어도
        닫기 버튼("×") 같은 마크업이 문서에 남는다 — 이 화면이 끼는 다른 페이지의
        "이 문구가 없어야 한다" 류 단언과 부딪힌다.
      */}
      {pendingDelete && (
        <ConfirmModal
          open
          title="증빙을 지울까요?"
          message={`${EVIDENCE_TYPE_LABEL[pendingDelete.evidenceType]} 파일을 지웁니다. 온체인에 이미 기록된 증빙은 지울 수 없습니다.`}
          // 파일 목록의 트리거 버튼과 이름이 같으면("삭제") 테스트도 사용자도 어느 쪽을
          // 눌렀는지 구분할 수 없다. 모달의 확인 버튼은 "지우기" 로 갈라 둔다.
          confirmLabel="지우기"
          destructive
          loading={remove.isPending}
          onConfirm={() => {
            remove.mutate(
              { evidenceFileId: pendingDelete.evidenceFileId, referenceType, referenceId },
              { onSettled: () => setPendingDelete(null) },
            )
          }}
          onClose={() => setPendingDelete(null)}
        />
      )}
    </div>
  )
}

function formatDateTime(value: string): string {
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return parsed.toLocaleString('ko-KR', { dateStyle: 'medium', timeStyle: 'short' })
}
