import { ImageSquare } from '@phosphor-icons/react'
import { useObjectUrl } from '../../../hooks/useObjectUrl.ts'
import { useEvidenceContent } from '../hooks/useEvidenceContent.ts'
import type { EvidenceThumbnail } from '../types/evidence.types.ts'

export interface EvidenceThumbnailImageProps {
  /** 없으면(아직 조회 중이거나 사진이 없는 소재) 자리표시 아이콘을 그린다. */
  thumbnail: EvidenceThumbnail | undefined
  alt: string
  className?: string
}

/**
 * 마켓플레이스 카드·상세의 대표 사진 한 칸.
 *
 * `<img src={contentPath}>` 로 바로 그리지 않는다 — `/content` 는 `Authorization` 헤더를
 * 요구하는데 브라우저가 스스로 보내는 `<img src>` 요청에는 그 헤더가 붙지 않는다
 * (`EvidencePreview.tsx` 와 같은 이유). `useEvidenceContent` 로 바이트를 받아 blob URL 로
 * 바꿔 그린다.
 *
 * 사진이 없거나(대상에 사진류 증빙이 없음) 아직 못 받았으면(로딩·실패) 같은 자리에
 * 아이콘을 그린다 — 목록 카드 높이가 사진 유무로 들쭉날쭉해지지 않게 한다.
 */
export function EvidenceThumbnailImage({ thumbnail, alt, className }: EvidenceThumbnailImageProps) {
  const content = useEvidenceContent(
    thumbnail?.evidenceFileId ?? 0,
    thumbnail?.contentPath ?? '',
    thumbnail !== undefined,
  )
  const url = useObjectUrl(content.data)

  if (url === null) {
    return <ImageSquare aria-hidden="true" className={className} />
  }

  return <img className={className} src={url} alt={alt} />
}
