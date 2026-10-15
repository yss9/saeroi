import { apiClient } from '../../../services/apiClient.ts'
import type { WeightRecognitionResult } from '../types/weightRecognition.types.ts'

/** 저울 사진을 AI 인식 파이프라인에 보낸다. */
export async function recognizeWeight(image: File): Promise<WeightRecognitionResult> {
  const formData = new FormData()
  formData.append('image', image)

  const { data } = await apiClient.post<WeightRecognitionResult>(
    '/materials/recognize-weight',
    formData,
    {
      // 공용 인스턴스의 application/json 기본값을 지워 Axios가 boundary를 붙이게 한다.
      headers: { 'Content-Type': undefined },
      timeout: 35_000,
    },
  )

  return data
}
