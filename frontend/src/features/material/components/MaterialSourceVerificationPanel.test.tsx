import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { MaterialDetail } from '../types/material.types.ts'
import { MaterialSourceVerificationPanel } from './MaterialSourceVerificationPanel.tsx'

const mocks = vi.hoisted(() => ({
  chainId: '0x79f5' as string | null,
  verify: vi.fn(),
  verifyState: {
    status: 'IDLE',
    hash: null as string | null,
    receipt: null as { blockNumber: number } | null,
    error: null,
    isBusy: false,
    isRejected: false,
  },
  readEvidenceHash: vi.fn(),
}))

vi.mock('../../auth/hooks/useAuth.ts', () => ({
  useAuth: () => ({ user: { id: 3 } }),
}))

// MaterialMintPanel 과 같은 이유로 walletStore 가 아니라 useWallet() 을 모킹한다.
vi.mock('../../wallet/hooks/useWallet.ts', () => ({
  useSwitchChainNotice: () => ({ message: null, report: () => {} }),
  useWallet: () => ({
    address: '0x053E6Ed40F29f21577472d99bF9Fa4AdECD4a63e',
    chainId: mocks.chainId,
    isConnected: true,
    connect: vi.fn(),
    disconnect: vi.fn(),
    switchChain: vi.fn(),
    wrongChain: false,
    expectedChainId: '0x79f5',
  }),
}))

vi.mock('../hooks/useVerifySourceOnChain.ts', () => ({
  useVerifySourceOnChain: () => ({ ...mocks.verifyState, verify: mocks.verify }),
}))

vi.mock('../services/materialContractService.ts', () => ({
  getOnChainMaterialEvidenceHash: mocks.readEvidenceHash,
}))

vi.mock('../../../config/contracts.ts', () => ({
  contracts: {
    materialToken: '0xBE98f5A9aA8d0480B09C689e38806aCcD41a0f66',
  },
}))

vi.mock('../../../config/env.ts', () => ({
  env: {
    besuChainIdHex: '0x79f5',
    besuExplorerUrl: null,
  },
}))

function material(overrides: Partial<MaterialDetail> = {}): MaterialDetail {
  return {
    materialId: 12,
    materialCode: 'MAT-000012',
    materialType: 'DENIM',
    name: '수거 데님',
    rootOrigin: 'WASTE',
    generationType: 'INITIAL',
    verificationState: 'REGISTERED',
    verification: null,
    status: 'ACTIVE',
    totalQuantity: 20_000,
    grade: null,
    description: null,
    lengthMm: null,
    widthMm: null,
    registrantId: 3,
    createdAt: '2026-09-08T00:00:00',
    owners: [],
    evidenceHash: null,
    manifestUrl: null,
    originReason: null,
    originReasonNote: null,
    composition: [],
    tokenId: 7,
    chainId: 31221,
    contractAddress: '0xBE98f5A9aA8d0480B09C689e38806aCcD41a0f66',
    ...overrides,
  }
}

describe('MaterialSourceVerificationPanel', () => {
  afterEach(cleanup)

  beforeEach(() => {
    mocks.chainId = '0x79f5'
    mocks.verify.mockReset()
    mocks.verifyState.status = 'IDLE'
    mocks.verifyState.hash = null
    mocks.verifyState.receipt = null
    mocks.readEvidenceHash.mockReset().mockResolvedValue(`0x${'ab'.repeat(32)}`)
  })

  it('사진 증빙으로 발행된 소재는 체인에 저장된 동일한 해시로 출처 검증한다', async () => {
    const user = userEvent.setup()
    render(<MaterialSourceVerificationPanel material={material()} />)

    await user.click(await screen.findByRole('button', { name: '출처 검증 서명' }))

    expect(mocks.verify).toHaveBeenCalledWith(expect.objectContaining({
      evidenceHash: `0x${'ab'.repeat(32)}`,
    }))
  })

  it('발행된 소재에 대해 verifySource 서명을 요청한다', async () => {
    const user = userEvent.setup()
    render(<MaterialSourceVerificationPanel material={material()} />)

    await user.click(screen.getByRole('button', { name: '출처 검증 서명' }))

    expect(mocks.verify).toHaveBeenCalledWith(
      expect.objectContaining({
        materialId: 12,
        tokenId: 7,
      }),
    )
  })

  it('아직 온체인에 발행되지 않은 소재는 서명을 막는다', () => {
    render(<MaterialSourceVerificationPanel material={material({ tokenId: null })} />)

    expect(screen.getByRole('button', { name: '출처 검증 서명' })).toBeDisabled()
    expect(screen.getByText('먼저 소재를 온체인에 발행해야 합니다.')).toBeInTheDocument()
  })

  it('이미 검증된 소재는 서명 버튼을 그리지 않는다', () => {
    render(
      <MaterialSourceVerificationPanel
        material={material({
          verification: {
            state: 'SOURCE_VERIFIED',
            signerWallet: '0x053E6Ed40F29f21577472d99bF9Fa4AdECD4a63e',
            signedAt: '2026-09-09T00:00:00',
            txHash: `0x${'cd'.repeat(32)}`,
          },
        })}
      />,
    )

    expect(screen.queryByRole('button', { name: '출처 검증 서명' })).not.toBeInTheDocument()
  })

  it('다른 체인에서는 서명을 막고 SSAFY 전환을 안내한다', () => {
    mocks.chainId = '0x7a69'
    render(<MaterialSourceVerificationPanel material={material()} />)

    expect(screen.getByRole('button', { name: '출처 검증 서명' })).toBeDisabled()
    expect(screen.getByText('MetaMask를 SSAFY 네트워크(31221)로 전환해 주세요.')).toBeInTheDocument()
  })

  it('확정된 트랜잭션의 해시와 블록 번호를 표시한다', () => {
    mocks.verifyState.status = 'CONFIRMED'
    mocks.verifyState.hash = `0x${'a'.repeat(64)}`
    mocks.verifyState.receipt = { blockNumber: 123_456 }

    render(<MaterialSourceVerificationPanel material={material()} />)

    expect(screen.getByText('123,456')).toBeInTheDocument()
    expect(screen.getByTitle(`0x${'a'.repeat(64)}`)).toBeInTheDocument()
  })
})
