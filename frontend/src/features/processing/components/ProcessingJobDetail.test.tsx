import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { useAuthStore } from '../../../stores/authStore.ts'
import { ProcessingJobDetail } from './ProcessingJobDetail.tsx'

const mocks = vi.hoisted(() => ({ useProcessingJob: vi.fn(), saveResult: vi.fn() }))

vi.mock('../hooks/useProcessingJobs.ts', () => ({
  useProcessingJob: mocks.useProcessingJob,
}))
vi.mock('../../evidence/components/EvidenceUpload.tsx', () => ({
  EvidenceUpload: ({ uploadMode }: { uploadMode?: string }) => (
    <div data-testid="evidence-upload" data-upload-mode={uploadMode}>증빙 업로드</div>
  ),
}))

const mutation = () => ({ isPending: false, error: null, mutate: vi.fn() })

beforeEach(() => {
  useAuthStore.setState({
    user: { id: 8, email: 'processor@test.com', name: '가공사', role: 'PROCESSOR', walletAddress: null },
    accessToken: 'token',
    isAuthenticated: true,
  })
  mocks.useProcessingJob.mockReturnValue({
    detail: {
      isPending: false,
      isError: false,
      data: {
        jobId: 17,
        jobCode: 'JOB-017',
        requestId: 3,
        quoteId: 5,
        brandId: 2,
        processorId: 8,
        fee: 10_000,
        paymentAmount: 10_000,
        status: 'PROCESSING',
        paymentExpiresAt: '2026-09-15T10:15:00',
        createdAt: '2026-09-15T10:00:00',
        inputMaterialId: 11,
        inputQuantity: 1_000,
        lossQuantity: 0,
        resultRevision: 0,
        carrier: null,
        trackingNumber: null,
        rejectionReason: null,
        shippedAt: null,
        receivedAt: null,
        startedAt: '2026-09-15T10:00:00',
        resultSubmittedAt: null,
        rejectedAt: null,
        completedAt: null,
        outputs: [],
      },
      refetch: vi.fn(),
    },
    request: {
      isPending: false,
      isError: false,
      data: {
        requestId: 3,
        requestCode: 'PR-003',
        brandId: 2,
        brandName: '새로이 브랜드',
        processorId: 8,
        processorName: '가공사',
        materialId: 11,
        materialCode: 'MAT-011',
        materialName: '폐데님 원단',
        materialType: 'DENIM',
        quantity: 1_000,
        processTypes: ['CLEANING', 'CUTTING'],
        status: 'SELECTED',
        createdAt: '2026-09-15T09:00:00',
        expiresAt: '2026-09-22T09:00:00',
      },
    },
    ship: mutation(),
    receive: mutation(),
    start: mutation(),
    saveResult: { ...mutation(), mutate: mocks.saveResult },
    submit: mutation(),
    approve: mutation(),
    reject: mutation(),
  })
})

afterEach(() => {
  cleanup()
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
  vi.clearAllMocks()
})

test('산출 수량이 바뀌면 Loss를 즉시 계산하고 투입량 초과 시 저장을 막는다', async () => {
  const user = userEvent.setup()
  render(<MemoryRouter><ProcessingJobDetail jobId={17} /></MemoryRouter>)

  expect(screen.getByLabelText('산출 수량 합계')).toHaveTextContent('0 g')
  expect(screen.getByLabelText('자동 계산 Loss')).toHaveTextContent('1,000 g')

  await user.type(screen.getByLabelText('산출물 1 소재명'), '재생 원단')
  await user.type(screen.getByLabelText('산출물 1 등급 판정 사유'), '오염과 손상이 없음')
  await user.type(screen.getByLabelText('산출물 1 산출 수량(g)'), '650')
  expect(screen.getByLabelText('산출 수량 합계')).toHaveTextContent('650 g')
  expect(screen.getByLabelText('자동 계산 Loss')).toHaveTextContent('350 g')
  expect(screen.getByRole('button', { name: '결과 임시 저장' })).toBeEnabled()

  await user.click(screen.getByRole('button', { name: '산출물 추가' }))
  await user.type(screen.getAllByLabelText(/소재명/)[1], '재생 부자재')
  await user.type(screen.getByLabelText('산출물 2 등급 판정 사유'), '오염과 손상이 없음')
  await user.type(screen.getByLabelText('산출물 2 산출 수량(g)'), '500')

  expect(screen.getByLabelText('산출 수량 합계')).toHaveTextContent('1,150 g')
  expect(screen.getByLabelText('자동 계산 Loss')).toHaveTextContent('0 g')
  expect(screen.getByRole('alert')).toHaveTextContent('투입량을 150 g 초과했습니다.')
  expect(screen.getByRole('button', { name: '결과 임시 저장' })).toBeDisabled()
})

test('가공 증빙은 종류별 업로드 모드로 표시한다', () => {
  render(<MemoryRouter><ProcessingJobDetail jobId={17} /></MemoryRouter>)

  expect(screen.getByTestId('evidence-upload')).toHaveAttribute('data-upload-mode', 'type-grid')
})
test('Processor에게 기술 ID 대신 요청 대상과 공정 정보를 보여준다', () => {
  render(<MemoryRouter><ProcessingJobDetail jobId={17} /></MemoryRouter>)

  expect(screen.getByRole('heading', { name: '폐데님 원단 가공' })).toBeInTheDocument()
  expect(screen.getByText('새로이 브랜드')).toBeInTheDocument()
  expect(screen.getByText('MAT-011')).toBeInTheDocument()
  expect(screen.getByText('세척')).toBeInTheDocument()
  expect(screen.getByText('재단')).toBeInTheDocument()
  expect(screen.queryByText('JOB-017')).not.toBeInTheDocument()
  expect(screen.queryByText('PROCESSING JOB')).not.toBeInTheDocument()
})

test('산출물 입력 의미를 라벨로 안내하고 선택한 등급과 판정 사유를 저장한다', async () => {
  const user = userEvent.setup()
  render(<MemoryRouter><ProcessingJobDetail jobId={17} /></MemoryRouter>)

  expect(screen.getByLabelText('산출물 1 소재 종류')).toBeInTheDocument()
  await user.type(screen.getByLabelText('산출물 1 소재명'), '재생 데님 원단')
  await user.selectOptions(screen.getByLabelText('산출물 1 품질 등급'), 'B')
  await user.type(screen.getByLabelText('산출물 1 등급 판정 사유'), '표면에 경미한 오염이 남아 있음')
  await user.type(screen.getByLabelText('산출물 1 산출 수량(g)'), '650')
  await user.click(screen.getByRole('button', { name: '결과 임시 저장' }))

  expect(mocks.saveResult).toHaveBeenCalledWith({
    outputs: [{
      name: '재생 데님 원단',
      grade: 'B',
      gradeReason: '표면에 경미한 오염이 남아 있음',
      quantity: 650,
    }],
  })
})

/*
  산출물의 소재 종류는 가공사가 고를 값이 아니다 — 데님을 맡겼는데 면사가 나올 수는 없다.
  가공은 섬유를 정제하고 모양을 바꿀 뿐 종류를 바꾸지 않는다. 화면은 투입 소재의 종류를
  보여 주기만 하고 보내지 않는다. 서버가 투입 소재에서 파생하므로 보낼 이유도 없다.
*/
test('산출 소재 종류는 투입 소재를 그대로 따르고 고를 수 없다', async () => {
  const user = userEvent.setup()
  render(<MemoryRouter><ProcessingJobDetail jobId={17} /></MemoryRouter>)

  const shown = screen.getByLabelText('산출물 1 소재 종류')
  expect(shown).toHaveTextContent('데님')
  expect(shown.tagName).not.toBe('SELECT')

  await user.type(screen.getByLabelText('산출물 1 소재명'), '재생 데님 원단')
  await user.type(screen.getByLabelText('산출물 1 등급 판정 사유'), '오염과 손상이 없음')
  await user.type(screen.getByLabelText('산출물 1 산출 수량(g)'), '650')
  await user.click(screen.getByRole('button', { name: '결과 임시 저장' }))

  const [payload] = mocks.saveResult.mock.calls[0]
  expect(payload.outputs[0]).not.toHaveProperty('materialType')
})
test('반환 발송 폼을 작업 카드 안에 중첩 카드 없이 표시한다', () => {
  const flow = mocks.useProcessingJob()
  mocks.useProcessingJob.mockReturnValue({
    ...flow,
    detail: { ...flow.detail, data: { ...flow.detail.data, status: 'RESULT_SUBMITTED' } },
    evidence: { data: { files: [] } },
    shipReturn: mutation(),
    returnRejected: mutation(),
  })

  const { container } = render(<MemoryRouter><ProcessingJobDetail jobId={17} /></MemoryRouter>)

  expect(screen.getByRole('heading', { name: '가공 결과물 반환 발송' })).toBeInTheDocument()
  expect(container.querySelector('.pj-panel .pj-panel')).not.toBeInTheDocument()
  expect(container.querySelector('.pj-panel .pj-shipment-form')).toBeInTheDocument()
})
