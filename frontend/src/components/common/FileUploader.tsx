import { useId, useState } from 'react'

export interface FileUploaderProps {
  /** 예: 'image/*', '.pdf,.png' */
  accept?: string
  multiple?: boolean
  /** 바이트 단위 개별 파일 최대 크기 */
  maxSizeBytes?: number
  label?: string
  disabled?: boolean
  onChange: (files: File[]) => void
}

const DEFAULT_MAX_SIZE = 10 * 1024 * 1024 // 10MB

/**
 * 증빙 파일 선택 UI.
 *
 * 업로드 자체는 하지 않는다 — 선택된 File 객체만 넘긴다. 실제 전송은 각 도메인의
 * Feature API 가 담당한다 (소재 증빙, 가공 전/후 사진, 분쟁 자료 등 엔드포인트가 다르다).
 *
 * TODO: 파일 저장소가 정해지면(S3 presigned URL vs Spring 직접 업로드)
 * 업로드 진행률 표시를 여기에 붙인다.
 */
export function FileUploader({
  accept,
  multiple = false,
  maxSizeBytes = DEFAULT_MAX_SIZE,
  label = '파일 선택',
  disabled,
  onChange,
}: FileUploaderProps) {
  const inputId = useId()
  const [rejected, setRejected] = useState<string[]>([])

  return (
    <div className="ui-field">
      <label className="ui-field__label" htmlFor={inputId}>
        {label}
      </label>
      <input
        id={inputId}
        className="ui-field__control"
        type="file"
        accept={accept}
        multiple={multiple}
        disabled={disabled}
        onChange={(event) => {
          const selected = Array.from(event.target.files ?? [])
          // 크기 초과 파일을 조용히 버리지 않는다. 사용자가 왜 안 올라갔는지 알아야 한다.
          const tooLarge = selected.filter((file) => file.size > maxSizeBytes)
          setRejected(tooLarge.map((file) => file.name))
          onChange(selected.filter((file) => file.size <= maxSizeBytes))
        }}
      />
      {rejected.length > 0 && (
        <p className="ui-field__error" role="alert">
          용량이 커서 제외됨: {rejected.join(', ')}
        </p>
      )}
    </div>
  )
}
