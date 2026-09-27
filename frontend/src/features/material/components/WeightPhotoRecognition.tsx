import { Scales } from '@phosphor-icons/react'
import { useEffect, useRef, useState } from 'react'
import { Button } from '../../../components/common/Button.tsx'
import { useObjectUrl } from '../../../hooks/useObjectUrl.ts'
import { recognizeWeight } from '../api/recognizeWeight.ts'
import type { WeightRecognitionResult } from '../types/weightRecognition.types.ts'
import './material.css'

export interface WeightPhotoRecognitionProps {
  onApply(weightGram: number): void
  onFileChange(file: File | null): void
}

const ACCEPTED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png'])
const MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024
const MIN_APPLICABLE_CONFIDENCE = 0.7
const FAILURE_FALLBACK = '무게를 인식하지 못했습니다.'

export function WeightPhotoRecognition({
  onApply,
  onFileChange,
}: WeightPhotoRecognitionProps) {
  const [file, setFile] = useState<File | null>(null)
  const [result, setResult] = useState<WeightRecognitionResult | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isRecognizing, setIsRecognizing] = useState(false)
  const requestIdRef = useRef(0)
  const previewUrl = useObjectUrl(file)

  useEffect(
    () => () => {
      requestIdRef.current += 1
    },
    [],
  )

  async function selectFile(selected: File | null) {
    const requestId = requestIdRef.current + 1
    requestIdRef.current = requestId
    setResult(null)
    setErrorMessage(null)

    if (!selected) {
      setFile(null)
      setIsRecognizing(false)
      onFileChange(null)
      return
    }

    if (!ACCEPTED_IMAGE_TYPES.has(selected.type)) {
      setFile(null)
      setIsRecognizing(false)
      setErrorMessage('JPG 또는 PNG 사진만 선택해 주세요.')
      onFileChange(null)
      return
    }

    if (selected.size > MAX_IMAGE_SIZE_BYTES) {
      setFile(null)
      setIsRecognizing(false)
      setErrorMessage('사진은 10MB 이하만 선택할 수 있습니다.')
      onFileChange(null)
      return
    }

    setFile(selected)
    setIsRecognizing(true)
    onFileChange(selected)

    try {
      const recognized = await recognizeWeight(selected)
      if (requestIdRef.current === requestId) setResult(recognized)
    } catch {
      if (requestIdRef.current === requestId) {
        setErrorMessage('일시적인 서비스 문제로 무게를 인식하지 못했습니다. 무게를 수동으로 입력해 주세요.')
      }
    } finally {
      if (requestIdRef.current === requestId) setIsRecognizing(false)
    }
  }

  const recognizedWeight = result?.weight ?? null
  const isCandidate =
    recognizedWeight !== null &&
    (result?.status === 'SUCCESS' || result?.status === 'LOW_CONFIDENCE')
  const canApply =
    isCandidate &&
    (result.status === 'SUCCESS' || result.confidence >= MIN_APPLICABLE_CONFIDENCE)
  const isRecognitionFailure = result !== null && !isCandidate

  return (
    <section className="weight-recognition" aria-labelledby="weight-recognition-title">
      <div className="weight-recognition__heading">
        <div>
          <h3 id="weight-recognition-title">저울 사진</h3>
          <p>숫자와 저울 화면이 선명하게 보이도록 촬영해 주세요.</p>
        </div>
        {file && (
          <Button
            type="button"
            variant="secondary"
            size="sm"
            onClick={() => void selectFile(null)}
          >
            사진 제거
          </Button>
        )}
      </div>

      {/*
        보이는 라벨은 두지 않는다 — 바로 위 제목("저울 사진")이 같은 말을 한다.
        이름은 aria-label 로 남긴다. 보조 기술에게는 이 칸의 유일한 이름이고, 제목과 같은
        말이면 구획과 칸이 한 이름을 나눠 가져 어느 쪽인지 가려지지 않는다.
      */}
      {/* 칸은 "선택된 파일 없음" 까지만 차지하고, 받는 조건은 그 옆 한 줄에 붙는다. */}
      <div className="weight-recognition__picker">
        <input
          id="weight-recognition-image"
          aria-label="저울 사진 선택"
          className="weight-recognition__file-input"
          type="file"
          accept="image/jpeg,image/png"
          disabled={isRecognizing}
          onChange={(event) => {
            const selected = event.target.files?.[0] ?? null
            event.target.value = ''
            void selectFile(selected)
          }}
        />
        <p className="weight-recognition__hint">JPG 또는 PNG · 최대 10MB</p>
      </div>

      {previewUrl && (
        <img
          className="weight-recognition__preview"
          src={previewUrl}
          alt="선택한 저울 사진 미리보기"
        />
      )}

      {isRecognizing && (
        <div className="weight-recognition__status" role="status">
          {/* 도는 표시가 있어야 "멈췄다" 가 아니라 "하고 있다" 로 읽힌다. */}
          <span className="ui-loading__spinner" aria-hidden="true" />
          <span>
            무게를 <strong>인식하는 중</strong>입니다…
          </span>
        </div>
      )}

      {errorMessage && (
        <div className="weight-recognition__alert" role="alert">
          {errorMessage}
        </div>
      )}

      {isRecognitionFailure && (
        <div className="weight-recognition__alert" role="alert">
          <p>{result.message?.trim() || FAILURE_FALLBACK}</p>
          <p>사진을 다시 촬영해 주세요.</p>
        </div>
      )}

      {isCandidate && (
        <div
          className={`weight-recognition__result${
            result.status === 'LOW_CONFIDENCE' ? ' weight-recognition__result--warning' : ''
          }`}
          role="status"
        >
          {/*
            인식 결과를 한 문장으로, 적용 버튼과 한 줄에 둔다 — 읽고 판단하고 누르는 일이 한
            호흡이다. OCR 원문은 두지 않는다. 인식된 무게와 같은 숫자를 한 번 더 적는 줄이었다.
          */}
          <div className="weight-recognition__result-row">
            {/*
              문장은 그대로 두고 조각마다 무게를 나눈다 — 숫자는 크게, 신뢰도는 주황으로,
              끝말은 조용히. 한 가지 굵기로 적으면 무엇이 결과인지 한눈에 들어오지 않는다.
            */}
            <p className="weight-recognition__weight">
              <Scales aria-hidden="true" weight="duotone" />
              <strong>
                {recognizedWeight.value.toLocaleString('ko-KR')}
                <small>{recognizedWeight.unit}</small>
              </strong>
              <span className="weight-recognition__confidence">
                (신뢰도 {Math.round(result.confidence * 100)}%)
              </span>
              <span className="weight-recognition__tail">로 측정되었습니다.</span>
            </p>
            <Button
              type="button"
              size="sm"
              disabled={!canApply}
              onClick={() => onApply(recognizedWeight.value)}
            >
              인식 무게 적용
            </Button>
          </div>
          {result.status === 'LOW_CONFIDENCE' && (
            <p>
              {result.message?.trim() || '인식 신뢰도가 낮습니다. 숫자를 확인해 주세요.'}
              {!canApply && ' 신뢰도가 낮아 적용할 수 없습니다. 사진을 다시 촬영해 주세요.'}
            </p>
          )}
        </div>
      )}
    </section>
  )
}
