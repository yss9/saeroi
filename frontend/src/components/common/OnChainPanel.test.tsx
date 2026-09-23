import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { OnChainPanel } from './OnChainPanel.tsx'
import { useWallet } from '../../features/wallet/hooks/useWallet.ts'

// 기대 체인 ID 는 빌드 시 주입되는 값이라 테스트 환경에는 없다. 없으면 컴포넌트가
// "어디로 바꾸라 할지 모른다" 고 보고 버튼을 두지 않으므로, 여기서 정해 준다.
vi.mock('../../config/env.ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../config/env.ts')>()
  return { ...actual, env: { ...actual.env, besuChainIdHex: '0x79f5' } }
})

vi.mock('../../features/wallet/hooks/useWallet.ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../features/wallet/hooks/useWallet.ts')>()
  return { ...actual, useWallet: vi.fn() }
})

const switchChain = vi.fn()

function mockWallet({ wrongChain, chainId }: { wrongChain: boolean; chainId?: string | null }) {
  vi.mocked(useWallet).mockReturnValue({
    address: '0xabc0000000000000000000000000000000000001',
    chainId: chainId !== undefined ? chainId : wrongChain ? '0x1' : '0x79f5',
    isConnecting: false,
    error: null,
    wrongChain,
    expectedChainId: '0x79f5',
    connect: vi.fn(),
    disconnect: vi.fn(),
    switchChain,
  } as unknown as ReturnType<typeof useWallet>)
}

function renderPanel(overrides: Partial<Parameters<typeof OnChainPanel>[0]> = {}) {
  return render(
    <OnChainPanel
      title="소재 발행"
      description="이 소재를 블록체인에 발행합니다."
      onChainId={null}
      actionable
      ready={false}
      blockedReason="MetaMask를 SSAFY 네트워크(31221)로 전환해 주세요."
      status="IDLE"
      hash={null}
      error={null}
      isBusy={false}
      isRejected={false}
      onSign={vi.fn()}
      actionLabel="소재 발행"
      {...overrides}
    />,
  )
}

beforeEach(() => {
  switchChain.mockReset()
  switchChain.mockResolvedValue('switched')
})

afterEach(cleanup)

// 이 화면은 "SSAFY 네트워크로 전환해 주세요" 라고만 말하고 전환할 방법을 주지 않았다.
// 전환 버튼은 회사 정보 화면에만 있었고, 사용자는 안내를 읽고도 여기서 아무것도 할 수
// 없었다 — 시키면서 수단을 주지 않는 화면이었다.
it('네트워크가 다르면 그 자리에서 전환할 수 있다', async () => {
  const user = userEvent.setup()
  mockWallet({ wrongChain: true })
  renderPanel()

  const button = screen.getByRole('button', { name: '연결 환경 변경' })
  await user.click(button)

  expect(switchChain).toHaveBeenCalledOnce()
})

it('네트워크가 맞으면 전환 버튼을 두지 않는다', () => {
  mockWallet({ wrongChain: false })
  renderPanel({ ready: true, blockedReason: undefined })

  expect(screen.queryByRole('button', { name: '연결 환경 변경' })).not.toBeInTheDocument()
})

it('전환을 취소하면 그 사실을 알린다', async () => {
  const user = userEvent.setup()
  switchChain.mockResolvedValue('rejected')
  mockWallet({ wrongChain: true })
  renderPanel()

  await user.click(screen.getByRole('button', { name: '연결 환경 변경' }))

  expect(await screen.findByText('연결 환경 변경을 취소했습니다.')).toBeInTheDocument()
})

// 이미 온체인에 올라간 단계에는 서명할 일이 없다 — 네트워크를 바꾸라고 할 이유도 없다.
it('이미 발행된 단계에는 전환 버튼을 두지 않는다', () => {
  mockWallet({ wrongChain: true })
  renderPanel({ onChainId: 12 })

  expect(screen.queryByRole('button', { name: '연결 환경 변경' })).not.toBeInTheDocument()
})

// 지갑은 붙어 있는데 체인을 아직 못 읽은 구간이 있다. 이때 부모 패널은
// `chainId === 기대값` 이 거짓이라 "전환해 주세요" 를 띄우는데, useWallet 의
// wrongChain 은 chainId 가 null 이면 거짓이라 버튼이 사라졌다 — 문구만 뜨고
// 누를 것이 없는 상태가 된다.
it('체인을 아직 못 읽었어도 전환 수단을 준다', () => {
  mockWallet({ wrongChain: false, chainId: null })
  renderPanel()

  expect(screen.getByRole('button', { name: '연결 환경 변경' })).toBeInTheDocument()
})

it('지갑이 연결되지 않았으면 전환 버튼을 두지 않는다', () => {
  vi.mocked(useWallet).mockReturnValue({
    address: null,
    chainId: null,
    wrongChain: false,
    expectedChainId: '0x79f5',
    connect: vi.fn(),
    disconnect: vi.fn(),
    switchChain,
  } as unknown as ReturnType<typeof useWallet>)
  renderPanel({ blockedReason: '지갑을 먼저 연결해 주세요.' })

  expect(screen.queryByRole('button', { name: '연결 환경 변경' })).not.toBeInTheDocument()
})
