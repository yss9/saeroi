import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { WeightRecognitionResult } from '../types/weightRecognition.types.ts'
import { WeightPhotoRecognition } from './WeightPhotoRecognition.tsx'

const api = vi.hoisted(() => ({
  recognizeWeight: vi.fn<() => Promise<WeightRecognitionResult>>(),
}))

vi.mock('../api/recognizeWeight.ts', () => api)

const successResult: WeightRecognitionResult = {
  status: 'SUCCESS',
  weight: { value: 1_250, unit: 'g' },
  ocr: { rawText: '1250 g', confidence: 0.97 },
  detection: { scaleConfidence: 0.96, displayConfidence: 0.95 },
  confidence: 0.95,
  message: null,
}

function imageFile(name = 'scale.jpg', type = 'image/jpeg', size = 12): File {
  const file = new File([new Uint8Array(Math.min(size, 12))], name, { type })
  Object.defineProperty(file, 'size', { value: size })
  return file
}

function renderRecognition() {
  const onApply = vi.fn()
  const onFileChange = vi.fn()
  const view = render(
    <WeightPhotoRecognition onApply={onApply} onFileChange={onFileChange} />,
  )
  return { ...view, onApply, onFileChange }
}

describe('WeightPhotoRecognition', () => {
  beforeEach(() => {
    api.recognizeWeight.mockReset()
    api.recognizeWeight.mockResolvedValue(successResult)
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:scale-preview')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
  })

  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  it.each([
    ['JPG', imageFile('scale.jpg', 'image/jpeg')],
    ['PNG', imageFile('scale.png', 'image/png')],
  ])('%s 사진을 선택하면 미리 보여 주고 인식을 시작한다', async (_label, file) => {
    const { onFileChange } = renderRecognition()
    const user = userEvent.setup()

    await user.upload(screen.getByLabelText('저울 사진 선택'), file)

    expect(onFileChange).toHaveBeenCalledWith(file)
    expect(screen.getByRole('img', { name: '선택한 저울 사진 미리보기' })).toHaveAttribute(
      'src',
      'blob:scale-preview',
    )
    await waitFor(() => expect(api.recognizeWeight).toHaveBeenCalledWith(file))
  })

  it('JPEG와 PNG가 아닌 파일은 요청 전에 거절한다', async () => {
    const { onFileChange } = renderRecognition()
    const user = userEvent.setup({ applyAccept: false })

    await user.upload(
      screen.getByLabelText('저울 사진 선택'),
      imageFile('scale.webp', 'image/webp'),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent('JPG 또는 PNG')
    expect(api.recognizeWeight).not.toHaveBeenCalled()
    expect(onFileChange).toHaveBeenLastCalledWith(null)
  })

  it('10MB를 초과한 파일은 요청 전에 거절한다', async () => {
    const { onFileChange } = renderRecognition()
    const user = userEvent.setup()

    await user.upload(
      screen.getByLabelText('저울 사진 선택'),
      imageFile('large.png', 'image/png', 10 * 1024 * 1024 + 1),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent('10MB 이하')
    expect(api.recognizeWeight).not.toHaveBeenCalled()
    expect(onFileChange).toHaveBeenLastCalledWith(null)
  })

  it('정확히 10MB인 PNG 사진은 허용한다', async () => {
    const { onFileChange } = renderRecognition()
    const user = userEvent.setup()
    const file = imageFile('limit.png', 'image/png', 10 * 1024 * 1024)

    await user.upload(screen.getByLabelText('저울 사진 선택'), file)

    expect(onFileChange).toHaveBeenCalledWith(file)
    await waitFor(() => expect(api.recognizeWeight).toHaveBeenCalledWith(file))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('인식하는 동안 진행 상태를 보여 주고 파일 입력을 잠근다', async () => {
    api.recognizeWeight.mockReturnValue(new Promise(() => {}))
    renderRecognition()
    const user = userEvent.setup()

    await user.upload(screen.getByLabelText('저울 사진 선택'), imageFile())

    expect(screen.getByRole('status')).toHaveTextContent('무게를 인식하는 중')
    expect(screen.getByLabelText('저울 사진 선택')).toBeDisabled()
  })

  it('성공 결과를 보여 주되 사용자가 적용하기 전에는 콜백을 부르지 않는다', async () => {
    const { onApply } = renderRecognition()
    const user = userEvent.setup()

    await user.upload(screen.getByLabelText('저울 사진 선택'), imageFile())

    expect(await screen.findByRole('status')).toHaveTextContent('1,250g(신뢰도 95%)로 측정되었습니다.')
    // OCR 원문은 인식 무게와 같은 숫자를 한 번 더 적는 줄이라 두지 않는다.
    expect(screen.getByRole('status')).not.toHaveTextContent('OCR 원문')
    expect(onApply).not.toHaveBeenCalled()
  })

  it('적용 버튼을 눌러야 인식된 gram 값을 넘긴다', async () => {
    const { onApply } = renderRecognition()
    const user = userEvent.setup()
    await user.upload(screen.getByLabelText('저울 사진 선택'), imageFile())

    await user.click(await screen.findByRole('button', { name: '인식 무게 적용' }))

    expect(onApply).toHaveBeenCalledOnce()
    expect(onApply).toHaveBeenCalledWith(1_250)
  })

  it('신뢰도 0.70인 LOW_CONFIDENCE 결과는 경고와 함께 적용할 수 있다', async () => {
    api.recognizeWeight.mockResolvedValue({
      ...successResult,
      status: 'LOW_CONFIDENCE',
      confidence: 0.7,
      message: '인식 신뢰도가 낮습니다.',
    })
    const { onApply } = renderRecognition()
    const user = userEvent.setup()
    await user.upload(screen.getByLabelText('저울 사진 선택'), imageFile())

    expect(await screen.findByRole('status')).toHaveTextContent('인식 신뢰도가 낮습니다.')
    const applyButton = screen.getByRole('button', { name: '인식 무게 적용' })
    expect(applyButton).toBeEnabled()

    await user.click(applyButton)
    expect(onApply).toHaveBeenCalledWith(1_250)
  })

  it('신뢰도가 0.70 미만이면 결과는 보여 주지만 적용을 막는다', async () => {
    api.recognizeWeight.mockResolvedValue({
      ...successResult,
      status: 'LOW_CONFIDENCE',
      confidence: 0.69,
      message: '숫자를 확인해 주세요.',
    })
    const { onApply } = renderRecognition()
    const user = userEvent.setup()
    await user.upload(screen.getByLabelText('저울 사진 선택'), imageFile())

    expect(await screen.findByRole('status')).toHaveTextContent('1,250g(신뢰도 69%)로 측정되었습니다.')
    expect(screen.getByRole('button', { name: '인식 무게 적용' })).toBeDisabled()
    expect(screen.getByRole('status')).toHaveTextContent('다시 촬영')
    expect(onApply).not.toHaveBeenCalled()
  })

  it.each([
    ['SCALE_NOT_FOUND', '저울을 찾지 못했습니다.'],
    ['DISPLAY_NOT_FOUND', null],
    ['OCR_FAILED', null],
    ['INVALID_WEIGHT_FORMAT', null],
    ['INVALID_IMAGE', null],
  ] satisfies Array<[WeightRecognitionResult['status'], string | null]>) (
    '%s 실패는 서버 메시지 또는 안정적인 안내와 재촬영 행동을 보여 준다',
    async (status, message) => {
      api.recognizeWeight.mockResolvedValue({
        status,
        weight: null,
        ocr: null,
        detection: null,
        confidence: 0,
        message,
      })
      renderRecognition()
      const user = userEvent.setup()
      await user.upload(screen.getByLabelText('저울 사진 선택'), imageFile())

      const alert = await screen.findByRole('alert')
      expect(alert).toHaveTextContent(message ?? '무게를 인식하지 못했습니다.')
      expect(alert).toHaveTextContent('사진을 다시 촬영해 주세요.')
      expect(screen.queryByRole('button', { name: '인식 무게 적용' })).not.toBeInTheDocument()
    },
  )

  it('인식 요청 자체가 실패하면 서비스 일시 장애와 수동 입력을 안내한다', async () => {
    api.recognizeWeight.mockRejectedValue(new Error('network detail'))
    renderRecognition()
    const user = userEvent.setup()

    await user.upload(screen.getByLabelText('저울 사진 선택'), imageFile())

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('일시적인 서비스 문제')
    expect(alert).toHaveTextContent('수동으로 입력')
    expect(alert).not.toHaveTextContent('다시 촬영')
  })

  it('사진을 바꾸거나 컴포넌트를 닫으면 이전 미리보기 URL을 해제한다', async () => {
    vi.mocked(URL.createObjectURL)
      .mockReturnValueOnce('blob:first')
      .mockReturnValueOnce('blob:second')
    const { unmount } = renderRecognition()
    const user = userEvent.setup()
    const input = screen.getByLabelText('저울 사진 선택')

    await user.upload(input, imageFile('first.jpg'))
    await screen.findByText(/측정되었습니다/)
    await user.upload(input, imageFile('second.png', 'image/png'))

    await waitFor(() => expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:first'))
    unmount()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:second')
  })

  it('파일 제거 시 선택을 비우고 부모에 null을 알린다', async () => {
    const { onFileChange } = renderRecognition()
    const user = userEvent.setup()
    await user.upload(screen.getByLabelText('저울 사진 선택'), imageFile())
    await screen.findByText(/측정되었습니다/)

    await user.click(screen.getByRole('button', { name: '사진 제거' }))

    expect(onFileChange).toHaveBeenLastCalledWith(null)
    expect(screen.queryByRole('img', { name: '선택한 저울 사진 미리보기' })).not.toBeInTheDocument()
    expect(screen.queryByText(/측정되었습니다/)).not.toBeInTheDocument()
  })
})
