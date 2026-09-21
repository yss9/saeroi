import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { useAuthStore } from '../../../stores/authStore.ts'
import { PROCESSING_CHAIN_STATUS } from '../services/processingContractService.ts'
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
function renderDetail({ role, job, chainStatusCode, chainStatusError }: {
  role: 'BRAND' | 'PROCESSOR'
  job: { status: string; chainJobId: number | null; chainReleaseRequired?: boolean; refundedAt?: string }
  // release-lock 패널은 "이미 풀렸다는 확실한 증거(CANCELED)" 가 없으면 그대로 둔다 —
  // 기본값은 "잠금이 아직 살아 있다" 쪽으로 둔다.
  chainStatusCode?: number
  // 체인 조회가 실패한 경우를 흉내 낸다 — chainStatusCode 는 NONE 으로 남지만
  // 패널은 접히면 안 된다(그건 CANCELED 가 확실할 때만 할 일이다).
  chainStatusError?: boolean
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
        refundedAt: job.refundedAt ?? null,
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
    chainStatusCode: chainStatusCode ?? PROCESSING_CHAIN_STATUS.ACCEPTED,
    chainStatus: { isError: chainStatusError ?? false, error: chainStatusError ? new Error('체인 상태를 불러오지 못했습니다.') : null },
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
  // chainReleaseRequired 가 false 인데도 role==='BRAND' 만으로 해제 패널이 뜨면 잡아낸다.
  expect(screen.queryByRole('button', { name: 'MetaMask로 온체인 잠금 해제' })).not.toBeInTheDocument()
})

test('온체인 등록 전에는 가공사에게 수락 버튼을 보이지 않는다', () => {
  renderDetail({ role: 'PROCESSOR', job: { status: 'PAID', chainJobId: null } })

  expect(screen.queryByRole('button', { name: /수락/ })).not.toBeInTheDocument()
  expect(screen.getByText(/브랜드가 온체인 작업을 등록하면/)).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'MetaMask로 온체인 잠금 해제' })).not.toBeInTheDocument()
})

test('가공사는 온체인 서명 한 번으로 수락한다', async () => {
  const user = userEvent.setup()
  renderDetail({ role: 'PROCESSOR', job: { status: 'PAID', chainJobId: 11 } })

  await user.click(screen.getByRole('button', { name: 'MetaMask로 요청 수락' }))

  expect(accept).toHaveBeenCalledOnce()
  expect(screen.queryByRole('button', { name: 'MetaMask로 온체인 잠금 해제' })).not.toBeInTheDocument()
})

test('거절돼 잠금이 남으면 브랜드에게 해제를 요청한다', async () => {
  const user = userEvent.setup()
  renderDetail({ role: 'BRAND', job: { status: 'DECLINED', chainJobId: 11, chainReleaseRequired: true }, chainStatusCode: PROCESSING_CHAIN_STATUS.ACCEPTED })

  await user.click(screen.getByRole('button', { name: 'MetaMask로 온체인 잠금 해제' }))

  expect(releaseChainLock).toHaveBeenCalledOnce()
})

test('체인 잠금이 이미 풀렸으면(CANCELED) 해제 패널이 사라진다', () => {
  // 서버 chainReleaseRequired 는 상태+chainJobId 만 보고 여전히 true 를 준다 — 해제
  // 성공 뒤에도 그대로다. 그래서 화면은 체인 상태로 한 번 더 걸러야 한다.
  renderDetail({ role: 'BRAND', job: { status: 'DECLINED', chainJobId: 11, chainReleaseRequired: true }, chainStatusCode: PROCESSING_CHAIN_STATUS.CANCELED })

  expect(screen.queryByRole('button', { name: 'MetaMask로 온체인 잠금 해제' })).not.toBeInTheDocument()
})

test('만료된 작업도 잠금이 남으면 해제를 안내한다', () => {
  // EXPIRED 는 "환불되었습니다" 로 끝나는 화면이 아니다 — 온체인 잠금은 서버가
  // 풀지 않으므로(cancelJob 은 브랜드 지갑/DEFAULT_ADMIN_ROLE 만 호출 가능), 만료
  // 되어도 chainReleaseRequired 가 true 면 해제 패널이 떠야 한다.
  renderDetail({ role: 'BRAND', job: { status: 'EXPIRED', chainJobId: 11, chainReleaseRequired: true } })

  expect(screen.getByRole('button', { name: 'MetaMask로 온체인 잠금 해제' })).toBeInTheDocument()
})

test('체인 상태를 알 수 없어도(조회 실패) 해제 패널은 그대로 남는다', () => {
  // 조회 실패면 chainStatusCode 가 NONE 으로 남는다 — CANCELED 라는 확실한 증거가
  // 없으므로 패널을 접지 않는다. 잠금이 남아 있는데 버튼이 없는 것보다, 이미
  // 풀렸는데 눌러서 되돌아가는 쪽이 낫다.
  renderDetail({
    role: 'BRAND',
    job: { status: 'DECLINED', chainJobId: 11, chainReleaseRequired: true },
    chainStatusCode: PROCESSING_CHAIN_STATUS.NONE,
    chainStatusError: true,
  })

  expect(screen.getByRole('button', { name: 'MetaMask로 온체인 잠금 해제' })).toBeInTheDocument()
  expect(screen.getByText(/이미 해제됐을 수도 있으니/)).toBeInTheDocument()
  // 체인 조회 실패는 패널 안 안내 문구로만 보여준다 — 30초마다 도는 백그라운드
  // 폴링이 실패할 때마다 전역 배너가 빨갛게 뜨면, 정작 방금 누른 뮤테이션의
  // 실패가 그 배너에 묻힌다.
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
})

test('뮤테이션 실패는 체인 조회 실패와 달리 전역 배너에 뜬다', () => {
  renderDetail({
    role: 'BRAND',
    job: { status: 'DECLINED', chainJobId: 11, chainReleaseRequired: true },
    chainStatusCode: PROCESSING_CHAIN_STATUS.ACCEPTED,
  })
  cleanup()
  const flow = mocks.useProcessingJob()
  mocks.useProcessingJob.mockReturnValue({
    ...flow,
    releaseChainLock: { ...flow.releaseChainLock, error: new Error('지갑 서명이 거부되었습니다.') },
  })

  render(<MemoryRouter><ProcessingJobDetail jobId={17} /></MemoryRouter>)

  expect(screen.getByRole('alert')).toHaveTextContent('지갑 서명이 거부되었습니다.')
})


// ── F3 · 구 순서로 만들어진 행의 온체인 등록 ────────────────────────────────────
//
// 새 순서에서 ACCEPTED 인 작업은 chainJobId 가 반드시 있다. 하지만 구 순서로
// 만들어진 행(DB 수락이 먼저였던 시절)은 ACCEPTED 인데 체인에 없을 수 있고, 서버의
// linkJob 은 그 경우를 위해 ACCEPTED 를 일부러 허용해 뒀다. 화면이 PAID 만 보면
// 그 허용은 눌러 볼 방법이 없는 규칙으로 남는다.
test('구 순서로 수락된 작업(ACCEPTED·체인 없음)도 브랜드가 온체인에 올릴 수 있다', () => {
  renderDetail({ role: 'BRAND', job: { status: 'ACCEPTED', chainJobId: null } })

  expect(screen.getByRole('button', { name: 'MetaMask로 온체인 작업 등록' })).toBeInTheDocument()
})

// ── F4 · 발송 폼의 체인 게이트 ────────────────────────────────────────────────
//
// markShipped 는 체인이 ACCEPTED 일 때만 통한다. 구 순서의 ACCEPTED 행은 체인이
// 아직 REQUESTED 라 버튼을 누르면 되돌아간다(revert) — 그 자리에는 버튼이 아니라
// 무엇을 기다리는지가 있어야 한다.
test('체인이 아직 REQUESTED 면 발송 폼 대신 수락을 기다린다고 알린다', () => {
  renderDetail({
    role: 'BRAND',
    job: { status: 'ACCEPTED', chainJobId: 11 },
    chainStatusCode: PROCESSING_CHAIN_STATUS.REQUESTED,
  })

  expect(screen.queryByRole('button', { name: 'MetaMask 확인 후 발송 처리' })).not.toBeInTheDocument()
  expect(screen.getByText('가공업체의 온체인 작업 수락을 기다리고 있습니다.')).toBeInTheDocument()
})

test('체인이 수락(ACCEPTED)됐으면 발송 폼을 연다', () => {
  renderDetail({
    role: 'BRAND',
    job: { status: 'ACCEPTED', chainJobId: 11 },
    chainStatusCode: PROCESSING_CHAIN_STATUS.ACCEPTED,
  })

  expect(screen.getByRole('button', { name: 'MetaMask 확인 후 발송 처리' })).toBeInTheDocument()
})

// 게이트는 "확실한 증거가 있을 때만 접는다" 는 같은 원칙을 따른다. 체인 조회가 아직
// 끝나지 않았거나 실패하면 chainStatusCode 는 NONE 으로 남는데, 그걸로 폼을 감추면
// 정상적인 새 순서 작업의 발송이 조회가 도는 동안 사라졌다가 나타난다.
test('체인 상태를 아직 모르면(NONE) 발송 폼을 감추지 않는다', () => {
  renderDetail({
    role: 'BRAND',
    job: { status: 'ACCEPTED', chainJobId: 11 },
    chainStatusCode: PROCESSING_CHAIN_STATUS.NONE,
  })

  expect(screen.getByRole('button', { name: 'MetaMask 확인 후 발송 처리' })).toBeInTheDocument()
})

// ── F6 · 잠금에 대한 정직한 문구 ──────────────────────────────────────────────
test('거절 확인 문구는 온체인 잠금이 브랜드 몫으로 남는다고 말한다', async () => {
  const user = userEvent.setup()
  renderDetail({ role: 'PROCESSOR', job: { status: 'PAID', chainJobId: 11 } })

  await user.click(screen.getByRole('button', { name: '거절' }))

  expect(screen.getByText(/온체인 잠금은 브랜드가 직접 해제/)).toBeInTheDocument()
  expect(screen.queryByText(/거절하면 가공비가 환불되고 소재 잠금이 풀립니다/)).not.toBeInTheDocument()
})

test('잠금이 남은 거절 화면은 "소재 수량도 풀렸습니다" 라고 말하지 않는다', () => {
  renderDetail({
    role: 'BRAND',
    job: { status: 'DECLINED', chainJobId: 11, chainReleaseRequired: true, refundedAt: '2026-09-15T11:00:00' },
    chainStatusCode: PROCESSING_CHAIN_STATUS.ACCEPTED,
  })

  // 바로 아래에 "잠금이 남아 있습니다" 라는 해제 패널이 함께 뜬다 — 두 문장이
  // 서로를 부정하면 브랜드는 어느 쪽을 믿어야 할지 알 수 없다.
  expect(screen.queryByText(/잠긴 소재 수량도 풀렸습니다/)).not.toBeInTheDocument()
  expect(screen.getByText(/온체인 잠금은 아직 남아 있습니다/)).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'MetaMask로 온체인 잠금 해제' })).toBeInTheDocument()
})

test('온체인에 올리기 전에 거절된 작업은 잠금까지 모두 풀렸다고 말한다', () => {
  renderDetail({
    role: 'BRAND',
    job: { status: 'DECLINED', chainJobId: null, refundedAt: '2026-09-15T11:00:00' },
  })

  expect(screen.getByText(/잠긴 소재 수량도 풀렸습니다/)).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'MetaMask로 온체인 잠금 해제' })).not.toBeInTheDocument()
})
