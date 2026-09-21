import { useState } from 'react'
import { Button, ErrorMessage, Loading } from '../../../components/common/index.ts'
import { useObjectUrl } from '../../../hooks/useObjectUrl.ts'
import { useEvidenceContent } from '../hooks/useEvidenceContent.ts'
import { evidenceContentKind, type EvidenceFile } from '../types/evidence.types.ts'

export interface EvidencePreviewProps {
  file: EvidenceFile
}

/**
 * 서버에 올라간 증빙 하나의 미리보기. S15P21E104-165.
 *
 * <h2>`<img src={contentPath}>` 로는 안 된다</h2>
 *
 * <p>조회 경로가 <b>`Authorization` 헤더를 요구</b>하는데 브라우저가 스스로 보내는
 * `<img src>` 요청에는 그 헤더가 붙지 않는다. 오리진이 같아도 401 이 오고, <b>파일을 볼
 * 자격이 있는 사용자에게도 이미지가 깨진다.</b> 그래서 `apiClient` 로 바이트를 받아
 * `blob:` URL 로 바꿔 그린다 (`api/getEvidenceContent.ts` 주석).
 *
 * <p>이 방식의 부수 효과가 오류 처리다. 자격이 없으면(`SUMMARY` 인 사용자·비회원)
 * 깨진 이미지 대신 <b>읽을 수 있는 오류</b>가 나온다 — `onError` 로 깨진 이미지를 뒤늦게
 * 감추는 것보다 낫다. 실제로는 `visibility: 'FULL'` 이 아니면 서버가 `evidenceFileId` 와
 * `contentPath` 를 아예 주지 않으므로 이 컴포넌트가 렌더될 일도 없다. 두 번째 방어다.
 *
 * <h2>사진은 바로 받고, 문서는 누르기 전에는 받지 않는다</h2>
 *
 * <p>한 소재에 증빙이 10개까지 붙고 파일당 10MB 다. <b>문서(pdf)</b>까지 목록을 그리는
 * 것만으로 전부 내려받으면 최악의 경우 100MB 가 조용히 흐르므로, 문서는 여전히 사용자가
 * 누른 뒤에 받는다. <b>사진</b>은 바로 보여준다 — 사진은 마켓플레이스에서도 관계 없는
 * 구매자에게 공개하는 종류라(`EvidenceAccessPolicy` 의 사진류 예외), 소재 등록자 본인이
 * 보는 이 화면에서 한 번 더 눌러야 하는 것이 오히려 어색하다.
 *
 * <h2>pdf 는 `<img>` 가 아니다</h2>
 *
 * <p>허용 목록에 `.pdf` 가 있다 (계근표·거래명세서). `<img>` 에 넣으면 깨진 이미지가 되고
 * 사용자는 파일이 잘못됐다고 생각한다. 그래서 새 탭에서 여는 링크로 준다 — pdf 를 페이지
 * 안에 심으면 브라우저 뷰어를 iframe 안에 가두게 되고, 화면이 좁으면 읽을 수 없다.
 */
export function EvidencePreview({ file }: EvidencePreviewProps) {
  const kind = evidenceContentKind(file.storageKey)
  const [requested, setRequested] = useState(kind === 'IMAGE')
  const content = useEvidenceContent(file.evidenceFileId, file.contentPath, requested)
  const url = useObjectUrl(content.data)

  // 표시 방법을 모르는 형식은 열기 버튼도 두지 않는다. 눌러도 보여줄 수 있는 것이 없고,
  // 해시와 등록 시각은 목록에 이미 있다.
  if (kind === null) {
    return <p className="evidence__preview-none">이 형식은 미리보기를 지원하지 않습니다.</p>
  }

  if (!requested) {
    return (
      <Button variant="secondary" size="sm" onClick={() => setRequested(true)}>
        {kind === 'PDF' ? '문서 열기' : '미리보기'}
      </Button>
    )
  }

  if (content.isPending) return <Loading label="증빙 파일을 불러오는 중" />

  if (content.isError) {
    return <ErrorMessage error={content.error} onRetry={() => void content.refetch()} />
  }

  // blob URL은 effect가 만든다. 준비 전에는 <img src="">를 그리지 않고 기다린다.
  if (url === null) return null

  if (kind === 'PDF') {
    return (
      // blob URL 이라 새 탭이 우리 오리진의 문서를 여는 것이 아니다. rel 은 그래도 둔다.
      <a className="evidence__preview-link" href={url} target="_blank" rel="noreferrer">
        문서를 새 탭에서 봅니다
      </a>
    )
  }

  return (
    <img
      className="ui-file-preview evidence__preview-image"
      src={url}
      // 사진 내용을 우리가 알 수 없으므로 무엇의 증빙인지로 대체한다. 빈 alt 를 두면
      // 스크린 리더 사용자는 이 칸이 있다는 것조차 모른다.
      alt={`증빙 ${file.evidenceFileId} 미리보기`}
    />
  )
}
