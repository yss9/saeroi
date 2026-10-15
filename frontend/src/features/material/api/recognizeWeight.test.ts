import type { AxiosResponse } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../../services/apiClient.ts'
import type { WeightRecognitionResult } from '../types/weightRecognition.types.ts'
import { recognizeWeight } from './recognizeWeight.ts'

describe('recognizeWeight', () => {
  afterEach(() => vi.restoreAllMocks())

  it('image multipart와 35초 제한으로 요청하고 벗겨진 인식 결과를 반환한다', async () => {
    const image = new File(['scale'], 'scale.jpg', { type: 'image/jpeg' })
    const result: WeightRecognitionResult = {
      status: 'SUCCESS',
      weight: { value: 845, unit: 'g' },
      ocr: { rawText: '845 g', confidence: 0.98 },
      detection: { scaleConfidence: 0.96, displayConfidence: 0.97 },
      confidence: 0.96,
      message: null,
    }
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: result } as AxiosResponse)

    await expect(recognizeWeight(image)).resolves.toBe(result)

    expect(post).toHaveBeenCalledOnce()
    const [path, body, config] = post.mock.calls[0]
    expect(path).toBe('/materials/recognize-weight')
    expect(body).toBeInstanceOf(FormData)
    expect((body as FormData).get('image')).toBe(image)
    expect(config).toMatchObject({ timeout: 35_000 })
    expect(config?.headers).toHaveProperty('Content-Type', undefined)
  })
})
