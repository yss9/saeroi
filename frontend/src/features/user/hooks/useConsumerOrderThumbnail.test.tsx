import { StrictMode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { consumerOrderKeys } from '../api/consumerOrderKeys.ts'
import { useConsumerOrderThumbnail } from './useConsumerOrderThumbnail.ts'

const api = vi.hoisted(() => ({
  getConsumerOrderThumbnail: vi.fn(),
}))

vi.mock('../api/getConsumerOrderThumbnail.ts', () => api)

function CachedThumbnail({ imageUrl }: { imageUrl: string }) {
  const thumbnail = useConsumerOrderThumbnail(imageUrl)
  return thumbnail.url
    ? <img src={thumbnail.url} alt="주문 상품" />
    : <span>이미지 준비 중</span>
}

describe('useConsumerOrderThumbnail', () => {
  beforeEach(() => {
    vi.spyOn(URL, 'createObjectURL')
      .mockReturnValueOnce('blob:cached-first-mount')
      .mockReturnValueOnce('blob:cached-strict-remount')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
  })

  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
    vi.resetAllMocks()
  })

  it('캐시된 blob으로 다시 마운트해도 활성 URL을 사용하고 언마운트 때 해제한다', async () => {
    const imageUrl = '/api/v1/product-models/10/thumbnail'
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, staleTime: Infinity } },
    })
    queryClient.setQueryData(
      [...consumerOrderKeys.details(), 'thumbnail', imageUrl],
      new Blob(['cached thumbnail'], { type: 'image/png' }),
    )

    const view = render(
      <StrictMode>
        <QueryClientProvider client={queryClient}>
          <CachedThumbnail imageUrl={imageUrl} />
        </QueryClientProvider>
      </StrictMode>,
    )

    const image = await screen.findByRole('img', { name: '주문 상품' })
    expect(api.getConsumerOrderThumbnail).not.toHaveBeenCalled()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:cached-first-mount')
    expect(URL.revokeObjectURL).not.toHaveBeenCalledWith('blob:cached-strict-remount')
    expect(image).toHaveAttribute('src', 'blob:cached-strict-remount')

    view.unmount()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:cached-strict-remount')
  })
})
