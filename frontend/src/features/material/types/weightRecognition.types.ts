/** AI 저울 사진 인식 파이프라인의 상태 계약. */
export type WeightRecognitionStatus =
  | 'INVALID_IMAGE'
  | 'SCALE_NOT_FOUND'
  | 'DISPLAY_NOT_FOUND'
  | 'OCR_FAILED'
  | 'INVALID_WEIGHT_FORMAT'
  | 'LOW_CONFIDENCE'
  | 'SUCCESS'

export interface RecognizedWeight {
  value: number
  unit: 'g'
}

export interface WeightOcrResult {
  rawText: string
  confidence: number
}

export interface WeightDetectionResult {
  scaleConfidence: number
  displayConfidence: number
}

/** `apiClient`가 ApiResponse 봉투를 벗긴 뒤 컴포넌트가 받는 값. */
export interface WeightRecognitionResult {
  status: WeightRecognitionStatus
  weight: RecognizedWeight | null
  ocr: WeightOcrResult | null
  detection: WeightDetectionResult | null
  confidence: number
  message: string | null
}
