import { StrictMode } from 'react'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useObjectUrl } from './useObjectUrl.ts'

function ObjectUrlImage({ source }: { source: Blob | null }) {
  const url = useObjectUrl(source)
  return url ? <img src={url} alt="blob 미리보기" /> : <span>미리보기 준비 중</span>
}

describe('useObjectUrl', () => {
  beforeEach(() => {
    vi.spyOn(URL, 'createObjectURL')
      .mockReturnValueOnce('blob:first-mount')
      .mockReturnValueOnce('blob:strict-remount')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
  })

  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  it('StrictMode가 effect를 다시 실행하면 해제되지 않은 새 URL을 보여준다', async () => {
    const source = new Blob(['cached image'], { type: 'image/png' })
    const view = render(
      <StrictMode>
        <ObjectUrlImage source={source} />
      </StrictMode>,
    )

    const image = await screen.findByRole('img', { name: 'blob 미리보기' })
    expect(URL.createObjectURL).toHaveBeenCalledTimes(2)
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:first-mount')
    expect(URL.revokeObjectURL).not.toHaveBeenCalledWith('blob:strict-remount')
    expect(image).toHaveAttribute('src', 'blob:strict-remount')

    view.unmount()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:strict-remount')
  })

  it('URL이 없을 때 빈 src 이미지를 만들지 않는다', async () => {
    const view = render(<ObjectUrlImage source={null} />)

    expect(screen.queryByRole('img')).not.toBeInTheDocument()
    expect(document.querySelector('img[src=""]')).not.toBeInTheDocument()

    view.rerender(<ObjectUrlImage source={new Blob(['image'])} />)
    await waitFor(() => expect(screen.getByRole('img')).toHaveAttribute('src', 'blob:first-mount'))

    view.rerender(<ObjectUrlImage source={null} />)
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:first-mount')
  })
})
