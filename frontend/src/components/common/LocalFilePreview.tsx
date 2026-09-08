import { useObjectUrl } from '../../hooks/useObjectUrl.ts'
import { isPreviewableImage } from './fileValidation.ts'

export interface LocalFilePreviewProps {
  file: File
}

/**
 * 사용자가 방금 고른 파일의 **로컬** 미리보기 한 칸. S15P21E104-253.
 *
 * <p>서버를 거치지 않는다 — 업로드 전에는 서버에 그 파일이 없고, 브라우저는 이미
 * 바이트를 갖고 있다.
 *
 * <h2>이미지가 아니면 `<img>` 를 쓰지 않는다</h2>
 *
 * <p>허용 목록에 `.pdf` 가 있는 도메인이 있다 (증빙의 계근표·거래명세서). pdf 를
 * `<img src>` 에 넣으면 **깨진 이미지 아이콘**이 뜨고, 사용자는 파일이 잘못됐다고
 * 생각한다. 그래서 확장자를 글자로 보여준다 — "이 파일은 있고, 그림으로 보여줄 수만
 * 없다" 를 말하는 것이 정확하다.
 */
export function LocalFilePreview({ file }: LocalFilePreviewProps) {
  // 이미지가 아니면 blob URL 을 아예 만들지 않는다. 쓰지 않을 URL 을 만들어 두면
  // 해제 시점만 늘어난다.
  const url = useObjectUrl(isPreviewableImage(file.type) ? file : null)

  if (url === null) {
    return (
      <span className="ui-file-preview ui-file-preview--none">{extensionLabel(file.name)}</span>
    )
  }

  return <img className="ui-file-preview" src={url} alt={`${file.name} 미리보기`} />
}

/** 확장자가 없는 파일도 있다. 그때는 "파일" 이라고 말한다 — 빈 칸보다 낫다 */
function extensionLabel(name: string): string {
  const dot = name.lastIndexOf('.')
  if (dot <= 0 || dot === name.length - 1) return '파일'
  return name.slice(dot + 1).toUpperCase()
}
