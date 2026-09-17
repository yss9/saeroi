// 제품 등록 폼.
//
// 이 폼의 핵심은 **등록이 두 번의 요청이라는 것**이다. 이미지를 먼저 올려 받은 저장 키가
// 등록 요청의 필수 필드다. 그래서 틀리면 안 되는 지점이 셋이다.
//
//   1) **이미지 없이 등록을 보내지 않는다** — 서버가 thumbnailUrl 을 @NotBlank 로 받는다.
//      보내면 400 이고, 사용자는 다 채운 폼에서 이유를 모르는 실패를 본다.
//   2) **업로드로 받은 키를 그대로 넘긴다** — 파일 이름이나 blob URL 을 넣으면 등록은
//      성공하는데 이미지가 영원히 안 보인다.
//   3) **등록이 판매 시작이 아니라는 것을 말한다** — 응답은 언제나 DRAFT·재고 0 이다.
//
// 모킹 경계는 API 모듈 두 개다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { ProductImageUpload, ProductModel } from '../types/product.types.ts'
import { ProductForm } from './ProductForm.tsx'

const api = vi.hoisted(() => ({
  uploadProductImage: vi.fn(),
  createProductModel: vi.fn(),
  updateProductModel: vi.fn(),
  replaceProductImages: vi.fn(),
}))
vi.mock('../api/uploadProductImage.ts', () => ({ uploadProductImage: api.uploadProductImage }))
vi.mock('../api/replaceProductImages.ts', () => ({
  replaceProductImages: api.replaceProductImages,
}))
vi.mock('../api/createProductModel.ts', () => ({ createProductModel: api.createProductModel }))
vi.mock('../api/updateProductModel.ts', () => ({ updateProductModel: api.updateProductModel }))

const UPLOADED: ProductImageUpload = {
  storageKey: 'product-images/2026/09/abc123.png',
  originalFilename: 'tote.png',
  contentType: 'image/png',
  size: 204_800,
  hash: '9f'.repeat(32),
}

const CREATED: ProductModel = {
  productModelId: 31,
  name: '업사이클 데님 토트백',
  category: 'BAG',
  brandId: 5,
  price: 129_000,
  description: '폐데님을 재단해 만든 토트백입니다.',
  thumbnailUrl: UPLOADED.storageKey,
  status: 'DRAFT',
  stockQuantity: 0,
  createdAt: '2026-09-08T10:00:00',
}

function image(name = 'tote.png', type = 'image/png'): File {
  return new File(['fake-bytes'], name, { type })
}

function renderForm(onCreated?: (product: ProductModel) => void) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={queryClient}>
      <ProductForm onCreated={onCreated} />
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

/** 수정 모드. 지금 값이 채워진 채로 열린다. */
function renderEditForm(
  product: ProductModel = CREATED,
  onUpdated?: (product: ProductModel) => void,
) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={queryClient}>
      <ProductForm product={product} onUpdated={onUpdated} />
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

/** 이미지를 뺀 나머지 필드를 채운다. */
async function fillFields(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('제품명'), '업사이클 데님 토트백')
  await user.selectOptions(screen.getByLabelText('카테고리'), 'BAG')
  await user.type(screen.getByLabelText('판매가 (원)'), '129000')
  await user.type(screen.getByLabelText('제품 설명'), '폐데님을 재단해 만든 토트백입니다.')
}

beforeEach(() => {
  api.uploadProductImage.mockResolvedValue(UPLOADED)
  api.createProductModel.mockResolvedValue(CREATED)
  api.updateProductModel.mockResolvedValue({ ...CREATED, price: 99_000 })
})
afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('ProductForm', () => {
  // 이 테스트가 이 파일의 존재 이유다.
  it('이미지를 올리기 전에는 등록할 수 없다', async () => {
    const user = renderForm()
    await fillFields(user)

    expect(screen.getByRole('button', { name: '제품 등록' })).toBeDisabled()
    expect(api.createProductModel).not.toHaveBeenCalled()
  })

  /*
    상세 화면이 갤러리를 보여주므로 여러 장을 받는다. 그중 한 장이 대표다 — 목록과 마켓에
    걸리는 얼굴이고, 서버도 갤러리에 대표를 한 장만 허용한다.
  */
  it('여러 장을 올리고 그중 대표를 골라 등록한다', async () => {
    api.uploadProductImage
      .mockResolvedValueOnce({ ...UPLOADED, storageKey: 'key-1' })
      .mockResolvedValueOnce({ ...UPLOADED, storageKey: 'key-2' })
    api.replaceProductImages.mockResolvedValue([])
    const user = renderForm()
    await fillFields(user)

    await user.upload(screen.getByLabelText('제품 사진'), [image('a.png'), image('b.png')])
    await waitFor(() => expect(api.uploadProductImage).toHaveBeenCalledTimes(2))

    // 기본 대표는 첫 장이다. 두 번째를 대표로 바꾼다.
    const mains = await screen.findAllByRole('radio', { name: '대표' })
    await user.click(mains[1])
    await user.click(screen.getByRole('button', { name: '제품 등록' }))

    await waitFor(() => expect(api.createProductModel).toHaveBeenCalled())
    // 대표가 제품의 대표 이미지가 된다.
    expect(api.createProductModel.mock.calls[0][0].thumbnailUrl).toBe('key-2')
    // 갤러리는 대표가 맨 앞이다 — 서버가 배열 순서를 그대로 자리로 쓴다.
    await waitFor(() => expect(api.replaceProductImages).toHaveBeenCalled())
    expect(api.replaceProductImages.mock.calls[0][1]).toEqual([
      { storageKey: 'key-2', type: 'MAIN' },
      { storageKey: 'key-1', type: 'ANGLE' },
    ])
  })

  it('파일을 고르는 즉시 업로드한다', async () => {
    const user = renderForm()

    await user.upload(screen.getByLabelText('제품 사진'), image())

    // 제출을 기다리지 않는다 — 실패 지점을 분리하기 위해 즉시 올린다.
    await waitFor(() => expect(api.uploadProductImage).toHaveBeenCalledTimes(1))
    expect(api.uploadProductImage.mock.calls[0][0]).toBeInstanceOf(File)
    expect(api.createProductModel).not.toHaveBeenCalled()
  })

  it('업로드가 끝나면 등록할 수 있고, 받은 저장 키를 그대로 보낸다', async () => {
    const user = renderForm()
    await fillFields(user)
    await user.upload(screen.getByLabelText('제품 사진'), image())

    const submit = screen.getByRole('button', { name: '제품 등록' })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    await waitFor(() => expect(api.createProductModel).toHaveBeenCalledTimes(1))
    expect(api.createProductModel.mock.calls[0][0]).toEqual({
      name: '업사이클 데님 토트백',
      category: 'BAG',
      price: 129_000,
      description: '폐데님을 재단해 만든 토트백입니다.',
      // 파일 이름이나 blob URL 이 아니라 업로드 응답의 저장 키여야 한다.
      thumbnailUrl: UPLOADED.storageKey,
    })
  })

  it('업로드가 실패하면 등록을 막고 사유를 보여준다', async () => {
    api.uploadProductImage.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, {
        code: 'PRODUCT_IMAGE_TYPE_NOT_ALLOWED',
        message: '허용되지 않는 이미지 형식입니다.',
        status: 400,
      }),
    )
    const user = renderForm()
    await fillFields(user)

    await user.upload(screen.getByLabelText('제품 사진'), image())

    expect(await screen.findByRole('alert')).toHaveTextContent('허용되지 않는 이미지 형식')
    expect(screen.getByRole('button', { name: '제품 등록' })).toBeDisabled()
  })

  // 서버도 거절하지만(PRODUCT_IMAGE_TYPE_NOT_ALLOWED) 보내기 전에 막는다.
  // accept 속성은 파일 선택창의 힌트일 뿐 강제가 아니다.
  it('허용되지 않는 형식은 업로드하지 않는다', async () => {
    const user = renderForm()

    await user.upload(screen.getByLabelText('제품 사진'), image('spec.pdf', 'application/pdf'))

    expect(api.uploadProductImage).not.toHaveBeenCalled()
  })

  it('필수 항목을 비우면 등록하지 않고 어느 칸인지 알린다', async () => {
    const user = renderForm()
    await user.upload(screen.getByLabelText('제품 사진'), image())
    await waitFor(() => expect(screen.getByRole('button', { name: '제품 등록' })).toBeEnabled())

    await user.click(screen.getByRole('button', { name: '제품 등록' }))

    expect(await screen.findByText('제품명을 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('카테고리를 선택해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('제품 설명을 입력해 주세요.')).toBeInTheDocument()
    expect(api.createProductModel).not.toHaveBeenCalled()
  })

  // 서버는 소수를 조용히 버려 129000 으로 등록한다. 요청은 200 인데 가격만 달라진다.
  it('판매가에 소수를 넣으면 막는다', async () => {
    const user = renderForm()
    await fillFields(user)
    await user.clear(screen.getByLabelText('판매가 (원)'))
    await user.type(screen.getByLabelText('판매가 (원)'), '129000.5')
    await user.upload(screen.getByLabelText('제품 사진'), image())
    await waitFor(() => expect(screen.getByRole('button', { name: '제품 등록' })).toBeEnabled())

    await user.click(screen.getByRole('button', { name: '제품 등록' }))

    expect(await screen.findByText('판매가는 원 단위 정수로 입력해 주세요.')).toBeInTheDocument()
    expect(api.createProductModel).not.toHaveBeenCalled()
  })

  it('등록하면 폼을 비우고 결과를 넘긴다', async () => {
    const onCreated = vi.fn()
    const user = renderForm(onCreated)
    await fillFields(user)
    await user.upload(screen.getByLabelText('제품 사진'), image())
    await waitFor(() => expect(screen.getByRole('button', { name: '제품 등록' })).toBeEnabled())

    await user.click(screen.getByRole('button', { name: '제품 등록' }))

    await waitFor(() => expect(onCreated).toHaveBeenCalledWith(CREATED))
    // 브랜드는 제품을 여러 개 연달아 올린다. 남은 값이 다음 등록에 섞이면 안 된다.
    expect(screen.getByLabelText('제품명')).toHaveValue('')
    expect(screen.getByRole('button', { name: '제품 등록' })).toBeDisabled()
  })

  // 등록 결과는 언제나 DRAFT·재고 0 이다. 이 말을 빼면 사용자는 마켓에서 자기 제품을
  // 찾다가 없다고 생각한다.
  // 등록 뒤 할 일(배치 연결)은 등록 화면이 직접 보여준다 — 폼이 미리 설명하지 않는다.
  it('등록 폼에는 안내 문구를 두지 않는다', () => {
    renderForm()

    expect(screen.queryByText(/판매 준비\(DRAFT\)/)).not.toBeInTheDocument()
  })

  describe('수정 모드', () => {
    it('지금 값으로 채워진 채 열린다', () => {
      renderEditForm()

      expect(screen.getByLabelText('제품명')).toHaveValue('업사이클 데님 토트백')
      expect(screen.getByLabelText('판매가 (원)')).toHaveValue(129_000)
      expect(screen.getByRole('button', { name: '수정 저장' })).toBeInTheDocument()
    })

    // 카테고리를 바꾸면 이미 그 분류로 팔린 제품의 이력이 흔들린다.
    it('카테고리는 바꿀 수 없고 이유를 적는다', () => {
      renderEditForm()

      expect(screen.getByLabelText('카테고리')).toBeDisabled()
      expect(screen.getByText('카테고리는 등록 후 바꿀 수 없습니다.')).toBeInTheDocument()
    })

    // 이 테스트가 이 블록의 존재 이유다 — 원래 값을 다시 보내면 무엇을 고쳤는지가 사라진다.
    it('바꾼 필드만 보낸다', async () => {
      const user = renderEditForm()

      await user.clear(screen.getByLabelText('판매가 (원)'))
      await user.type(screen.getByLabelText('판매가 (원)'), '99000')
      await user.click(screen.getByRole('button', { name: '수정 저장' }))

      await waitFor(() => expect(api.updateProductModel).toHaveBeenCalled())
      expect(api.updateProductModel.mock.calls[0][0]).toBe(31)
      expect(api.updateProductModel.mock.calls[0][1]).toEqual({ price: 99_000 })
    })

    // 아무것도 안 바뀌었으면 서버가 400 을 준다. 눌러 보고 알게 하지 않는다.
    it('바뀐 것이 없으면 저장할 수 없다', () => {
      renderEditForm()

      expect(screen.getByRole('button', { name: '수정 저장' })).toBeDisabled()
    })

    // 등록과 달리 이미지는 선택이다. 안 고르면 쓰던 이미지가 남는다.
    it('이미지를 고르지 않아도 저장할 수 있다', async () => {
      const user = renderEditForm()

      await user.clear(screen.getByLabelText('제품명'))
      await user.type(screen.getByLabelText('제품명'), '새 이름')
      await user.click(screen.getByRole('button', { name: '수정 저장' }))

      await waitFor(() => expect(api.updateProductModel).toHaveBeenCalled())
      expect(api.updateProductModel.mock.calls[0][1]).toEqual({ name: '새 이름' })
      expect(api.uploadProductImage).not.toHaveBeenCalled()
    })

    it('이미 결제된 주문은 영향받지 않는다고 알린다', () => {
      renderEditForm()

      expect(screen.getByText(/이미 결제된 주문은 영향받지/)).toBeInTheDocument()
    })
  })

  // 서버는 0 이상만 검사하지만, 이 값을 넘으면 카카오페이가 결제를 거부한다 — 그 사실이
  // 결제창에서 502 로만 드러난다.
  it('판매가 상한을 넘기면 등록하지 않는다', async () => {
    const user = renderForm()

    await user.type(screen.getByLabelText('제품명'), '비싼 가방')
    await user.selectOptions(screen.getByLabelText('카테고리'), 'BAG')
    await user.type(screen.getByLabelText('판매가 (원)'), '2147483648')
    await user.type(screen.getByLabelText('제품 설명'), '설명')
    await user.upload(screen.getByLabelText(/제품 사진/), image())
    await waitFor(() => expect(api.uploadProductImage).toHaveBeenCalled())

    await user.click(screen.getByRole('button', { name: '제품 등록' }))

    expect(await screen.findByText(/2,147,483,647원을 넘을 수 없습니다/)).toBeInTheDocument()
    expect(api.createProductModel).not.toHaveBeenCalled()
  })
})
