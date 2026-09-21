import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { useAuthStore } from '../../../stores/authStore.ts'
import { ProcessingJobDetail } from './ProcessingJobDetail.tsx'

const mocks = vi.hoisted(() => ({ useProcessingJob: vi.fn(), saveResult: vi.fn(), accept: vi.fn(), releaseChainLock: vi.fn() }))
const accept = mocks.accept
const releaseChainLock = mocks.releaseChainLock

vi.mock('../hooks/useProcessingJobs.ts', () => ({
  useProcessingJob: mocks.useProcessingJob,
}))
vi.mock('../../evidence/components/EvidenceUpload.tsx', () => ({
  EvidenceUpload: ({ uploadMode }: { uploadMode?: string }) => (
    <div data-testid="evidence-upload" data-upload-mode={uploadMode}>증빙 업로드</div>
  ),
}))

const mutation = () => ({ isPending: false, error: null, mutate: vi.fn() })

/**
 * PAID/DECLINED 단계의 단일 온체인 수락 흐름을 검증하는 테스트 전용 렌더 헬퍼.
 * 위 `beforeEach` 의 기본 픽스처(PROCESSING 상태)와 겹치지 않도록 role·job 을 직접 받는다.
 */
function renderDetail({ role, job }: {
  role: 'BRAND' | 'PROCESSOR'
  job: { status: string; chainJobId: number | null; chainReleaseRequired?: boolean }
}) {
  useAuthStore.setState({
    user: { id: role === 'PROCESSOR' ? 8 : 2, email: 'user@test.com', name: role === 'PROCESSOR' ? '가공사' : '새로이 브랜드', role, walletAddress: null },
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
        brandId: 2,
        processorId: 8,
        fee: 10_000,
        paymentAmount: 10_000,
        status: job.status,
        paymentExpiresAt: '2026-09-15T10:15:00',
        createdAt: '2026-09-15T10:00:00',
        chainJobId: job.chainJobId,
        inputMaterialId: 11,
        inputQuantity: 1_000,
        lossQuantity: 0,
        resultRevision: 0,
        carrier: null,
        trackingNumber: null,
        returnCarrier: null,
        returnTrackingNumber: null,
        returnShippingEvidenceHash: null,
        rejectionReason: null,
        shippedAt: null,
        receivedAt: null,
        startedAt: null,
        resultSubmittedAt: null,
        rejectedAt: null,
        returnShippedAt: null,
        completedAt: null,
        acceptExpiresAt: null,
        acceptedAt: null,
        declinedAt: null,
        declineReason: null,
        refundedAt: null,
        chainReleaseRequired: job.chainReleaseRequired ?? false,
        outputs: [],
      },
      refetch: vi.fn(),
    },
    request: { isPending: false, isError: false, data: undefined },
    evidence: { data: { files: [] } },
    initializeChain: mutation(),
    accept: { ...mutation(), mutate: accept },
    declineJob: mutation(),
    releaseChainLock: { ...mutation(), mutate: releaseChainLock },
    ship: mutation(),
    receive: mutation(),
    start: mutation(),
    saveResult: mutation(),
    submit: mutation(),
    shipReturn: mutation(),
    approve: mutation(),
    reject: mutation(),
    returnRejected: mutation(),
  })
  return render(<MemoryRouter><ProcessingJobDetail jobId={17} /></MemoryRouter>)
}

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
      materialType: 'DENIM',
      name: '재생 데님 원단',
      grade: 'B',
      gradeReason: '표면에 경미한 오염이 남아 있음',
      quantity: 650,
    }],
  })
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

test('결제 직후에는 브랜드가 온체인 등록을 먼저 한다', () => {
  renderDetail({ role: 'BRAND', job: { status: 'PAID', chainJobId: null } })

  expect(screen.getByRole('button', { name: 'MetaMask로 온체인 작업 등록' })).toBeInTheDocument()
})

test('온체인 등록 전에는 가공사에게 수락 버튼을 보이지 않는다', () => {
  renderDetail({ role: 'PROCESSOR', job: { status: 'PAID', chainJobId: null } })

  expect(screen.queryByRole('button', { name: /수락/ })).not.toBeInTheDocument()
  expect(screen.getByText(/브랜드가 온체인 작업을 등록하면/)).toBeInTheDocument()
})

test('가공사는 온체인 서명 한 번으로 수락한다', async () => {
  const user = userEvent.setup()
  renderDetail({ role: 'PROCESSOR', job: { status: 'PAID', chainJobId: 11 } })

  await user.click(screen.getByRole('button', { name: 'MetaMask로 요청 수락' }))

  expect(accept).toHaveBeenCalledOnce()
})

test('거절돼 잠금이 남으면 브랜드에게 해제를 요청한다', async () => {
  const user = userEvent.setup()
  renderDetail({ role: 'BRAND', job: { status: 'DECLINED', chainJobId: 11, chainReleaseRequired: true } })

  await user.click(screen.getByRole('button', { name: 'MetaMask로 온체인 잠금 해제' }))

  expect(releaseChainLock).toHaveBeenCalledOnce()
})
