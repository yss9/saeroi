// 소재 등록 폼. **전송 본문이 Backend 계약과 맞는가**를 화면 단위로 고정한다.
//
// 모킹 경계는 API 모듈 하나다 (WalletLoginButton.test.tsx 와 같은 방식).
// 그래야 react-hook-form 검증 → 값 변환 → 전송까지 실제로 통과한다.
//
// vitest 설정에 globals 가 없어 testing-library 자동 cleanup 이 등록되지 않는다 —
// afterEach 에서 직접 부른다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactElement } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MaterialForm } from './MaterialForm.tsx'

const api = vi.hoisted(() => ({ createMaterial: vi.fn() }))
vi.mock('../api/createMaterial.ts', () => api)

const evidenceApi = vi.hoisted(() => ({ uploadEvidence: vi.fn() }))
vi.mock('../../evidence/api/uploadEvidence.ts', () => evidenceApi)

const weightApi = vi.hoisted(() => ({ recognizeWeight: vi.fn() }))
vi.mock('../api/recognizeWeight.ts', () => weightApi)

/**
 * "이전 기록 불러오기" 는 보유 소재 목록에서 가장 최근 항목의 상세를 받아와 채운다.
 * 목록·상세 조회 API 를 각각 모킹해 실제 네트워크에 나가지 않게 한다.
 */
const materialApi = vi.hoisted(() => ({ getMaterial: vi.fn() }))
vi.mock('../api/getMaterial.ts', () => materialApi)

const myMaterials = vi.hoisted(() => ({ useMyMaterials: vi.fn() }))
vi.mock('../../inventory/hooks/useMyMaterials.ts', () => myMaterials)

/**
 * <b>지갑은 연결된 것으로 둔다.</b>
 *
 * 폼은 지갑이 없으면 DB 에 손대기 전에 멈춘다 — 체인에 영영 오르지 않을 소재를
 * 만들지 않기 위해서다. 이 파일이 고정하려는 것은 <b>전송 본문이 Backend 계약과
 * 맞는가</b> 이므로, 그 앞의 관문은 통과시켜 둔다. 관문 자체는 아래
 * "지갑 미연결" describe 가 따로 지킨다.
 */
const wallet = vi.hoisted(() => ({ address: '0xabc' as string | null, connect: vi.fn() }))
vi.mock('../../wallet/hooks/useWallet.ts', () => ({
  useWallet: () => wallet,
}))

/**
 * 서명은 이 파일의 관심사가 아니다. 실제 훅을 쓰면 window.ethereum 을 찾다 실패하고,
 * 폼이 catch 로 삼켜 `onCreated` 가 불리지 않는다 — 전송 본문 검사와 무관한 이유로
 * 테스트가 깨진다.
 */
const mint = vi.hoisted(() => ({ mint: vi.fn(), status: 'IDLE', isBusy: false, isRejected: false, error: null }))
vi.mock('../hooks/useMintMaterialOnChain.ts', () => ({
  useMintMaterialOnChain: () => mint,
}))

function renderForm(onCreated?: (id: number) => void): ReactElement {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false }, queries: { retry: false } },
  })
  const ui = (
    <QueryClientProvider client={queryClient}>
      <MaterialForm onCreated={onCreated} />
    </QueryClientProvider>
  )
  render(ui)
  return ui
}

/** 필수 항목만 채운다. 수량은 kg 단위로 입력한다. */
/**
 * 폼은 네 구획을 <b>한 번에 하나씩</b> 보여준다. 입력 12개를 한 화면에 쌓으면
 * 어디까지 했는지 알 수 없어서 나눈 것이다. 그래서 테스트도 그 길을 따라간다.
 *
 * <b>등록 버튼은 오른쪽에 늘 떠 있다</b> — 필수를 다 채운 사람은 끝까지 넘기지 않고
 * 바로 등록할 수 있어야 한다. 그래서 대부분의 테스트는 2구획에서 바로 제출한다.
 */
async function nextStep(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: /^다음:/ }))
}

/** 지나온 구획으로 되돌아간다. 단계 바의 버튼은 방문한 곳만 눌린다. */
async function backTo(user: ReturnType<typeof userEvent.setup>, title: string) {
  await user.click(screen.getByRole('button', { name: new RegExp(title) }))
}

async function fillRequired(quantityKg = '20.5') {
  const user = userEvent.setup()
  await user.selectOptions(screen.getByLabelText('소재 유형'), 'DENIM')
  await user.type(screen.getByLabelText('소재명'), '폐데님 원단')
  await nextStep(user)
  await user.clear(screen.getByLabelText('수량 (kg)'))
  await user.type(screen.getByLabelText('수량 (kg)'), quantityKg)
  return user
}

function weightPhoto(name = 'scale.jpg'): File {
  return new File(['scale-photo'], name, { type: 'image/jpeg' })
}

async function selectWeightPhoto(user: ReturnType<typeof userEvent.setup>, photo: File) {
  await user.upload(screen.getByLabelText('저울 사진'), photo)
  await screen.findByRole('button', { name: '인식 무게 적용' })
}

const uploadedWeightEvidence = {
  evidenceFileId: 7,
  storageKey: 'MATERIAL/41/weight/scale.jpg',
  contentPath: '/api/evidence/7/content',
  fileHash: '0xactualhash',
  evidenceType: 'WEIGHT_PHOTO',
  uploadedBy: 1,
  uploadedAt: '2026-09-16T00:00:00Z',
  deletedAt: null,
}

function submitted() {
  expect(api.createMaterial).toHaveBeenCalledTimes(1)
  return api.createMaterial.mock.calls[0][0]
}

beforeEach(() => {
  api.createMaterial.mockReset()
  api.createMaterial.mockResolvedValue({ materialId: 41, materialCode: 'MAT-000041' })
  evidenceApi.uploadEvidence.mockReset()
  evidenceApi.uploadEvidence.mockResolvedValue([uploadedWeightEvidence])
  weightApi.recognizeWeight.mockReset()
  weightApi.recognizeWeight.mockResolvedValue({
    status: 'SUCCESS',
    weight: { value: 2_350, unit: 'g' },
    ocr: { rawText: '2350 g', confidence: 0.98 },
    detection: { scaleConfidence: 0.97, displayConfidence: 0.96 },
    confidence: 0.96,
    message: null,
  })
  wallet.address = '0xabc'
  mint.mint.mockReset()
  mint.mint.mockResolvedValue(true)
  materialApi.getMaterial.mockReset()
  myMaterials.useMyMaterials.mockReset()
  myMaterials.useMyMaterials.mockReturnValue({ data: [] })
})

afterEach(cleanup)

describe('수량 환산', () => {
  it('kg 소수 입력을 gram 정수로 바꿔 보낸다', async () => {
    // Backend quantity 는 정수만 받는다. 20.5 를 그대로 보내면 400 이다.
    renderForm()
    const user = await fillRequired('20.5')
    await user.click(screen.getByRole('button', { name: /소재 등록/ }))

    await waitFor(() => expect(api.createMaterial).toHaveBeenCalled())
    expect(submitted()).toMatchObject({ quantity: 20500, unit: 'G' })
  })

  it('부동소수점 오차가 섞이지 않는다', async () => {
    // 20.3 * 1000 은 20299.999999999996 이다. 직접 곱하면 1g 이 사라진다.
    renderForm()
    const user = await fillRequired('20.3')
    await user.click(screen.getByRole('button', { name: /소재 등록/ }))

    await waitFor(() => expect(api.createMaterial).toHaveBeenCalled())
    expect(submitted().quantity).toBe(20300)
  })

  it('1g 미만은 거부한다', async () => {
    // 0.0004kg 은 0g 이 되고, 서버가 "수량은 양수" 로 거절해 원인이 드러나지 않는다.
    renderForm()
    const user = await fillRequired('0.0004')
    await user.click(screen.getByRole('button', { name: /소재 등록/ }))

    expect(await screen.findByText(/최소 1g/)).toBeInTheDocument()
    expect(api.createMaterial).not.toHaveBeenCalled()
  })

  it('0 이하를 거부한다', async () => {
    renderForm()
    const user = await fillRequired('0')
    await user.click(screen.getByRole('button', { name: /소재 등록/ }))

    expect(await screen.findByText('수량은 0보다 커야 합니다.')).toBeInTheDocument()
    expect(api.createMaterial).not.toHaveBeenCalled()
  })

  it('인식된 2350g을 적용하면 수량 입력에 2.35kg을 보여 준다', async () => {
    renderForm()
    const user = await fillRequired()
    const photo = weightPhoto()

    await selectWeightPhoto(user, photo)
    await user.click(screen.getByRole('button', { name: '인식 무게 적용' }))

    expect(weightApi.recognizeWeight).toHaveBeenCalledWith(photo)
    expect(screen.getByLabelText('수량 (kg)')).toHaveValue(2.35)
  })
})

describe('저울 사진 증빙과 온체인 발행', () => {
  it('선택한 사진을 생성된 소재의 중량 증빙으로 올리고 실제 해시로 발행한다', async () => {
    renderForm()
    const user = await fillRequired()
    const photo = weightPhoto()
    await selectWeightPhoto(user, photo)

    await user.click(screen.getByRole('button', { name: /소재 등록/ }))

    await waitFor(() => {
      expect(evidenceApi.uploadEvidence).toHaveBeenCalledWith(expect.objectContaining({
        referenceType: 'MATERIAL',
        referenceId: 41,
        evidenceType: 'WEIGHT_PHOTO',
        files: [photo],
      }))
    })
    expect(mint.mint).toHaveBeenCalledWith(expect.objectContaining({
      evidenceHash: '0xactualhash',
    }))
  })

  it('증빙 업로드가 실패하면 발행하지 않고 같은 소재와 사진으로 업로드만 재시도한다', async () => {
    evidenceApi.uploadEvidence
      .mockRejectedValueOnce(new Error('upload failed'))
      .mockResolvedValueOnce([uploadedWeightEvidence])
    renderForm()
    const user = await fillRequired()
    const photo = weightPhoto('first-scale.jpg')
    await selectWeightPhoto(user, photo)

    await user.click(screen.getByRole('button', { name: /소재 등록/ }))

    expect(await screen.findByRole('alert')).toHaveTextContent('증빙 업로드')
    expect(api.createMaterial).toHaveBeenCalledTimes(1)
    expect(evidenceApi.uploadEvidence).toHaveBeenCalledTimes(1)
    expect(mint.mint).not.toHaveBeenCalled()
    expect(screen.queryByLabelText('저울 사진')).not.toBeInTheDocument()
    expect(screen.getByText('first-scale.jpg')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '소재 등록 완료하기' }))

    await waitFor(() => expect(mint.mint).toHaveBeenCalledTimes(1))
    expect(api.createMaterial).toHaveBeenCalledTimes(1)
    expect(evidenceApi.uploadEvidence).toHaveBeenCalledTimes(2)
    expect(evidenceApi.uploadEvidence.mock.calls[1][0].files).toEqual([photo])
  })

  it('증빙 업로드가 빈 목록을 반환하면 실패로 처리하고 발행하지 않는다', async () => {
    evidenceApi.uploadEvidence.mockResolvedValue([])
    renderForm()
    const user = await fillRequired()
    await selectWeightPhoto(user, weightPhoto())

    await user.click(screen.getByRole('button', { name: /소재 등록/ }))

    expect(await screen.findByRole('alert')).toHaveTextContent('증빙 업로드')
    expect(mint.mint).not.toHaveBeenCalled()
  })

  it('증빙 업로드 중에는 완료 버튼을 잠가 같은 사진의 중복 업로드를 막는다', async () => {
    let finishUpload: ((files: typeof uploadedWeightEvidence[]) => void) | undefined
    evidenceApi.uploadEvidence.mockReturnValue(
      new Promise<typeof uploadedWeightEvidence[]>((resolve) => {
        finishUpload = resolve
      }),
    )
    renderForm()
    const user = await fillRequired()
    await selectWeightPhoto(user, weightPhoto())

    await user.click(screen.getByRole('button', { name: /소재 등록/ }))
    await waitFor(() => expect(evidenceApi.uploadEvidence).toHaveBeenCalledTimes(1))

    expect(screen.getByRole('status')).toHaveTextContent('저울 사진 증빙을 업로드')

    const finishButton = screen.getByRole('button', { name: '소재 등록 완료하기' })
    expect(finishButton).toBeDisabled()
    await user.click(finishButton)
    expect(evidenceApi.uploadEvidence).toHaveBeenCalledTimes(1)

    await act(async () => finishUpload?.([uploadedWeightEvidence]))
    await waitFor(() => expect(mint.mint).toHaveBeenCalledTimes(1))
  })

  it('발행이 실패하면 생성과 증빙 업로드를 반복하지 않고 같은 증빙 해시로 재시도한다', async () => {
    mint.mint
      .mockRejectedValueOnce(new Error('signature rejected'))
      .mockResolvedValueOnce(true)
    renderForm()
    const user = await fillRequired()
    await selectWeightPhoto(user, weightPhoto())

    await user.click(screen.getByRole('button', { name: /소재 등록/ }))
    await waitFor(() => expect(mint.mint).toHaveBeenCalledTimes(1))
    await user.click(screen.getByRole('button', { name: '소재 등록 완료하기' }))

    await waitFor(() => expect(mint.mint).toHaveBeenCalledTimes(2))
    expect(api.createMaterial).toHaveBeenCalledTimes(1)
    expect(evidenceApi.uploadEvidence).toHaveBeenCalledTimes(1)
    expect(mint.mint.mock.calls[1][0]).toMatchObject({ evidenceHash: '0xactualhash' })
  })

  it('사진이 없으면 증빙을 올리지 않고 기존 소재 코드 placeholder로 발행한다', async () => {
    renderForm()
    const user = await fillRequired()

    await user.click(screen.getByRole('button', { name: /소재 등록/ }))

    await waitFor(() => expect(mint.mint).toHaveBeenCalledTimes(1))
    expect(evidenceApi.uploadEvidence).not.toHaveBeenCalled()
    expect(mint.mint).toHaveBeenCalledWith(expect.objectContaining({
      evidenceHash: '0x00000000000000000000000000000000000000000000000000077027d675ebaa',
    }))
  })

  it('등록이 끝나면 선택한 사진과 재시도 상태를 비운다', async () => {
    const onCreated = vi.fn()
    renderForm(onCreated)
    const user = await fillRequired()
    await selectWeightPhoto(user, weightPhoto())
    expect(screen.getByRole('img', { name: '선택한 저울 사진 미리보기' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /소재 등록/ }))

    await waitFor(() => expect(onCreated).toHaveBeenCalledWith(41))
    expect(screen.queryByRole('img', { name: '선택한 저울 사진 미리보기' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /소재 등록/ })).toBeInTheDocument()
  })
})

describe('Backend 계약', () => {
  it('서버가 파생하거나 고정하는 필드를 보내지 않는다', async () => {
    // materialCode: 서버가 id 에서 파생 / generationType: 항상 INITIAL
    // registrantId: JWT sub. 보내면 무시되지만, 폼이 값을 만들면 사용자가
    // 입력한 것이 반영된다고 오해한다.
    renderForm()
    const user = await fillRequired()
    await user.click(screen.getByRole('button', { name: /소재 등록/ }))

    await waitFor(() => expect(api.createMaterial).toHaveBeenCalled())
    const payload = submitted()
    expect(payload).not.toHaveProperty('materialCode')
    expect(payload).not.toHaveProperty('generationType')
    expect(payload).not.toHaveProperty('registrantId')
    expect(payload).not.toHaveProperty('totalQuantity')
  })

  it('소재 코드 입력칸이 화면에 없다', async () => {
    renderForm()
    expect(screen.queryByLabelText('소재 코드')).not.toBeInTheDocument()
  })

  it('출처에 RECOVERED 가 없다', async () => {
    // 정책 v3에서 회수 소재 재순환이 MVP 범위에서 제외됐고 Backend ENUM 도 2종뿐이다.
    renderForm()
    const options = screen
      .getAllByRole('option')
      .map((option) => (option as HTMLOptionElement).value)

    expect(options).not.toContain('RECOVERED')
    expect(options).toContain('WASTE')
    expect(options).toContain('VIRGIN')
  })

  it('소재 유형을 자유 입력이 아니라 목록으로 고른다', async () => {
    renderForm()
    const select = screen.getByLabelText('소재 유형')
    expect(select.tagName).toBe('SELECT')

    const values = Array.from(select.querySelectorAll('option'))
      .map((option) => option.value)
      .filter(Boolean)
    expect(values).toEqual(['DENIM', 'COTTON', 'LEATHER', 'LINING', 'METAL', 'MIXED', 'ETC'])
  })

  it('빈 선택 항목을 빈 문자열로 보내지 않는다', async () => {
    // grade 는 자유 텍스트 컬럼이라 '' 를 보내면 "등급 없음"(null)과 다른 값이 저장된다.
    renderForm()
    const user = await fillRequired()
    await user.click(screen.getByRole('button', { name: /소재 등록/ }))

    await waitFor(() => expect(api.createMaterial).toHaveBeenCalled())
    const payload = submitted()
    expect(payload).not.toHaveProperty('grade')
    expect(payload).not.toHaveProperty('description')
  })

  it('선택 항목을 채우면 함께 보낸다', async () => {
    renderForm()
    const user = await fillRequired()
    // 등급은 1구획에 있다. 값은 구획을 넘어가도 남는다.
    await backTo(user, '기본 정보')
    await user.selectOptions(screen.getByLabelText('등급 (선택)'), 'B')
    await nextStep(user)
    await user.type(screen.getByLabelText('설명 (선택)'), '수거된 청바지')
    await user.type(screen.getByLabelText('길이 (mm, 선택)'), '1500')
    await user.click(screen.getByRole('button', { name: /소재 등록/ }))

    await waitFor(() => expect(api.createMaterial).toHaveBeenCalled())
    expect(submitted()).toMatchObject({ grade: 'B', description: '수거된 청바지', lengthMm: 1500 })
  })
})

describe('필수 검증', () => {
  it('소재 유형을 고르지 않으면 보내지 않는다', async () => {
    const user = userEvent.setup()
    renderForm()
    await user.type(screen.getByLabelText('소재명'), '폐데님')
    await user.click(screen.getByRole('button', { name: /소재 등록/ }))

    expect(await screen.findByText('소재 유형을 선택해 주세요.')).toBeInTheDocument()
    expect(api.createMaterial).not.toHaveBeenCalled()
  })

  it('소재명이 없으면 보내지 않는다', async () => {
    const user = userEvent.setup()
    renderForm()
    await user.selectOptions(screen.getByLabelText('소재 유형'), 'DENIM')
    await user.click(screen.getByRole('button', { name: /소재 등록/ }))

    expect(await screen.findByText('소재명을 입력해 주세요.')).toBeInTheDocument()
    expect(api.createMaterial).not.toHaveBeenCalled()
  })

  // 소재 유형은 ENUM 7종 고정이라 자유 입력을 서버로 보낼 수 없다(400). '기타'
  // 를 고르면 실제 종류가 어디에도 안 남지 않도록 설명을 사실상 필수로 만든다.
  it("소재 유형이 '기타' 인데 설명이 없으면 보내지 않는다", async () => {
    renderForm()
    const user = await fillRequired()
    await backTo(user, '기본 정보')
    await user.selectOptions(screen.getByLabelText('소재 유형'), 'ETC')
    await user.click(screen.getByRole('button', { name: /소재 등록/ }))

    expect(await screen.findByText('기타를 선택하면 설명이 필요합니다.')).toBeInTheDocument()
    expect(api.createMaterial).not.toHaveBeenCalled()
  })

  it("소재 유형이 '기타' 여도 설명을 채우면 보낸다", async () => {
    renderForm()
    const user = await fillRequired()
    await backTo(user, '기본 정보')
    await user.selectOptions(screen.getByLabelText('소재 유형'), 'ETC')
    await nextStep(user)
    await user.type(screen.getByLabelText(/설명/), '대나무 섬유')
    await user.click(screen.getByRole('button', { name: /소재 등록/ }))

    await waitFor(() => expect(api.createMaterial).toHaveBeenCalledTimes(1))
    expect(submitted()).toMatchObject({ materialType: 'ETC', description: '대나무 섬유' })
  })

  // 섬유 하나하나의 비율은 100%를 안 넘어도(60% + 60%) 합계는 넘을 수 있다.
  // 각 입력칸의 개별 validate 만으로는 이 조합을 못 잡아 등록이 그대로 됐었다.
  it('구성 비율 합계가 100%를 넘으면 등록 버튼이 막힌다', async () => {
    renderForm()
    const user = await fillRequired()
    await nextStep(user)

    await user.click(screen.getByRole('button', { name: '섬유 추가' }))
    await user.selectOptions(screen.getByLabelText('섬유 1'), 'COTTON')
    await user.type(screen.getByLabelText('비율 1 (%)'), '60')

    await user.click(screen.getByRole('button', { name: '섬유 추가' }))
    await user.selectOptions(screen.getByLabelText('섬유 2'), 'POLYESTER')
    await user.type(screen.getByLabelText('비율 2 (%)'), '60')

    expect(await screen.findByText(/100%를 넘을 수 없습니다/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /소재 등록/ })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: /소재 등록/ }))
    expect(api.createMaterial).not.toHaveBeenCalled()
  })
})

describe('등록 후', () => {
  it('materialId 를 onCreated 로 넘긴다', async () => {
    // 응답 필드 이름이 materialId 다 (id 가 아니다). 잘못 읽으면 상세로 이동하는
    // 주소가 /materials/undefined 가 된다.
    const onCreated = vi.fn()
    renderForm(onCreated)
    const user = await fillRequired()
    await user.click(screen.getByRole('button', { name: /소재 등록/ }))

    await waitFor(() => expect(onCreated).toHaveBeenCalledWith(41))
  })

  it('실패하면 에러를 보여주고 onCreated 를 부르지 않는다', async () => {
    const onCreated = vi.fn()
    api.createMaterial.mockRejectedValue({ kind: 'SERVER', message: '등록에 실패했습니다.' })
    renderForm(onCreated)
    const user = await fillRequired()
    await user.click(screen.getByRole('button', { name: /소재 등록/ }))

    expect(await screen.findByText('등록에 실패했습니다.')).toBeInTheDocument()
    expect(onCreated).not.toHaveBeenCalled()
  })
})

/**
 * <b>지갑 없이는 소재를 만들지 않는다.</b>
 *
 * 예전에는 소재를 먼저 만들고 발행만 조용히 건너뛰었다. 화면은 성공으로 넘어가는데
 * 체인에는 아무것도 없어서, 사용자는 등록했다고 믿지만 실제로는 <b>영영 체인에 오르지
 * 않을 고아</b>가 DB 에 남는다. 온체인 증명이 이 서비스의 존재 이유라 그건 등록이 아니다.
 *
 * LAN 으로 붙는 팀원에게서 그대로 드러났다 — `192.168.30.118:5173` 은
 * `localhost:5173` 과 다른 origin 이라 MetaMask 연결 승인이 따로 필요한데, 그 사실이
 * 어디에도 표시되지 않아 "왜 서명 창이 안 뜨지" 로 시간을 썼다.
 */
describe('지갑 미연결', () => {
  it('소재를 만들지 않는다', async () => {
    // 핵심이다. 순서가 중요한데, 만든 뒤에 막으면 이미 고아가 생긴 상태다.
    wallet.address = null
    renderForm()
    const user = await fillRequired()

    await user.click(screen.getByRole('button', { name: /소재 등록/ }))

    expect(api.createMaterial).not.toHaveBeenCalled()
  })

  it('무엇을 해야 하는지 알려준다', async () => {
    // 조용히 멈추면 예전(조용히 건너뛰기)과 똑같이 사용자를 헤매게 한다.
    wallet.address = null
    renderForm()
    const user = await fillRequired()

    await user.click(screen.getByRole('button', { name: /소재 등록/ }))

    expect(await screen.findByRole('alert')).toHaveTextContent('지갑을 먼저 연결해 주세요')
    expect(screen.getByRole('button', { name: '지갑 연결' })).toBeInTheDocument()
  })

  it('주소마다 승인이 따로라는 것을 설명한다', async () => {
    // "지갑을 연결해 주세요" 만으로는, 다른 기기에서 이미 연결해 둔 사용자가 왜 또
    // 연결해야 하는지 알 수 없다. 이번 혼선의 원인이 정확히 이것이었다.
    wallet.address = null
    renderForm()
    const user = await fillRequired()

    await user.click(screen.getByRole('button', { name: /소재 등록/ }))

    expect(await screen.findByRole('alert')).toHaveTextContent('지금 주소에서는 다시 승인')
  })

  it('연결 버튼은 지갑 연결을 부른다', async () => {
    wallet.address = null
    renderForm()
    const user = await fillRequired()
    await user.click(screen.getByRole('button', { name: /소재 등록/ }))

    await user.click(screen.getByRole('button', { name: '지갑 연결' }))

    expect(wallet.connect).toHaveBeenCalled()
  })

  it('제출 전에는 경고를 띄우지 않는다', () => {
    // 지갑 복원은 비동기라 화면이 뜨자마자 잠깐 null 이다. 그때부터 경고를 그리면
    // 정상적으로 연결된 사용자에게도 한 번 번쩍인다.
    wallet.address = null
    renderForm()

    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})

/**
 * 이전 기록 불러오기 + 동일 소재 재등록 확인.
 *
 * "가장 최근 소재" 는 보유 소재 목록에서 materialId 가 가장 큰 항목으로 판단하고,
 * 그 상세를 받아 폼을 채운다. 채운 값에서 아무것도 안 바꾸고 제출하면 확인 모달이
 * 뜨고, 하나라도 바꾸면 모달 없이 바로 등록된다.
 */
describe('이전 기록 불러오기', () => {
  const detail = {
    materialCode: 'MAT-000010',
    materialType: 'DENIM' as const,
    name: '폐데님 원단 이전본',
    rootOrigin: 'WASTE' as const,
    generationType: 'INITIAL' as const,
    verificationState: 'REGISTERED' as const,
    status: 'ACTIVE' as const,
    totalQuantity: 15_000,
    grade: 'B' as const,
    verification: null,
    materialId: 10,
    description: '이전 등록 설명',
    lengthMm: 1200,
    widthMm: 800,
    registrantId: 1,
    createdAt: '2026-09-01T00:00:00Z',
    owners: [],
    evidenceHash: null,
    manifestUrl: null,
    originReason: 'OFFCUT' as const,
    originReasonNote: '',
    composition: [{ fiberType: 'COTTON', percentage: 80 }],
    tokenId: null,
    chainId: null,
    contractAddress: null,
  }

  beforeEach(() => {
    myMaterials.useMyMaterials.mockReturnValue({
      data: [
        { materialId: 3, materialCode: 'MAT-000003', syncedAt: '2026-09-01T00:00:00Z' },
        { materialId: 10, materialCode: 'MAT-000010', syncedAt: '2026-09-02T00:00:00Z' },
      ],
    })
    materialApi.getMaterial.mockResolvedValue(detail)
  })

  it('보유 소재가 없으면 버튼이 비활성화된다', () => {
    myMaterials.useMyMaterials.mockReturnValue({ data: [] })
    renderForm()

    expect(screen.getByRole('button', { name: '이전 기록 불러오기' })).toBeDisabled()
  })

  it('가장 최근(materialId 최대) 소재의 상세를 불러와 폼을 채운다', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.click(screen.getByRole('button', { name: '이전 기록 불러오기' }))

    await waitFor(() => expect(materialApi.getMaterial).toHaveBeenCalledWith(10))
    expect(await screen.findByLabelText('소재명')).toHaveValue('폐데님 원단 이전본')

    // 수량은 2구획이다. 불러온 값은 구획을 넘어가도 남아 있어야 한다.
    await nextStep(user)
    expect(screen.getByLabelText('수량 (kg)')).toHaveValue(15)
  })

  it('불러온 값을 하나도 안 바꾸고 제출하면 확인 모달을 띄운다', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.click(screen.getByRole('button', { name: '이전 기록 불러오기' }))
    await waitFor(() => expect(screen.getByLabelText('소재명')).toHaveValue('폐데님 원단 이전본'))

    await user.click(screen.getByRole('button', { name: /소재 등록/ }))

    // 닫혀 있는 <dialog> 는 항상 DOM에 있으므로(내용은 항상 렌더된다) 텍스트가
    // 아니라 열림 상태를 드러내는 role='dialog' 로 확인한다.
    expect(await screen.findByRole('dialog')).toHaveTextContent(
      '이전과 완전히 동일한 소재입니다. 맞습니까?',
    )
    expect(api.createMaterial).not.toHaveBeenCalled()
  })

  it('모달에서 확인을 누르면 등록이 진행된다', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.click(screen.getByRole('button', { name: '이전 기록 불러오기' }))
    await waitFor(() => expect(screen.getByLabelText('소재명')).toHaveValue('폐데님 원단 이전본'))

    await user.click(screen.getByRole('button', { name: /소재 등록/ }))
    await screen.findByRole('dialog')
    await user.click(screen.getByRole('button', { name: '확인' }))

    await waitFor(() => expect(api.createMaterial).toHaveBeenCalledTimes(1))
  })

  it('모달에서 취소를 누르면 등록이 진행되지 않고 폼 값이 남는다', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.click(screen.getByRole('button', { name: '이전 기록 불러오기' }))
    await waitFor(() => expect(screen.getByLabelText('소재명')).toHaveValue('폐데님 원단 이전본'))

    await user.click(screen.getByRole('button', { name: /소재 등록/ }))
    await screen.findByRole('dialog')
    await user.click(screen.getByRole('button', { name: '취소' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(api.createMaterial).not.toHaveBeenCalled()
    expect(screen.getByLabelText('소재명')).toHaveValue('폐데님 원단 이전본')
  })

  it('불러온 값을 하나라도 바꾸면 모달 없이 바로 등록된다', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.click(screen.getByRole('button', { name: '이전 기록 불러오기' }))
    await waitFor(() => expect(screen.getByLabelText('소재명')).toHaveValue('폐데님 원단 이전본'))

    await user.clear(screen.getByLabelText('소재명'))
    await user.type(screen.getByLabelText('소재명'), '수정된 소재명')

    await user.click(screen.getByRole('button', { name: /소재 등록/ }))

    await waitFor(() => expect(api.createMaterial).toHaveBeenCalledTimes(1))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
