import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { MaterialDetail } from '../types/material.types.ts'
import { MaterialMintPanel } from './MaterialMintPanel.tsx'

const mocks = vi.hoisted(() => ({
  chainId: '0x79f5' as string | null,
  mint: vi.fn(),
  mintState: {
    status: 'IDLE',
    hash: null as string | null,
    blockNumber: null as number | null,
    error: null,
    isBusy: false,
    isRejected: false,
  },
}))

vi.mock('../../auth/hooks/useAuth.ts', () => ({
  useAuth: () => ({ user: { id: 3 } }),
}))

// 패널이 walletStore 가 아니라 useWallet() 을 읽는다. 스토어는 useWallet() 을 부르는
// 컴포넌트가 떠 있을 때만 채워지는데 이 화면에는 그런 컴포넌트가 없어서, 스토어를 읽으면
// 지갑이 연결돼 있어도 주소가 null 이 된다.
vi.mock('../../wallet/hooks/useWallet.ts', () => ({
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

vi.mock('../hooks/useMintMaterialOnChain.ts', () => ({
  useMintMaterialOnChain: () => ({ ...mocks.mintState, mint: mocks.mint }),
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

const material: MaterialDetail = {
  id: 12,
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
  tokenId: null,
  chainId: null,
  contractAddress: null,
}

describe('MaterialMintPanel', () => {
  afterEach(cleanup)

  beforeEach(() => {
    mocks.chainId = '0x79f5'
    mocks.mint.mockReset()
    mocks.mintState.status = 'IDLE'
    mocks.mintState.hash = null
    mocks.mintState.blockNumber = null
  })

  it('SSAFY 체인의 연결 지갑으로 mintMaterial 발행을 요청한다', async () => {
    const user = userEvent.setup()
    render(<MaterialMintPanel material={material} />)

    expect(screen.getByText('SSAFY (31221)')).toBeInTheDocument()
    expect(screen.getByText('0x053E6E…D4a63e')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '온체인 발행' }))

    expect(mocks.mint).toHaveBeenCalledWith(
      expect.objectContaining({
        materialId: 12,
        to: '0x053E6Ed40F29f21577472d99bF9Fa4AdECD4a63e',
        amount: 20_000,
        rootOrigin: 'WASTE',
      }),
    )
  })

  it('다른 체인에서는 발행을 막고 SSAFY 전환을 안내한다', () => {
    mocks.chainId = '0x7a69'
    render(<MaterialMintPanel material={material} />)

    expect(screen.getByRole('button', { name: '온체인 발행' })).toBeDisabled()
    expect(screen.getByText('MetaMask를 SSAFY 네트워크(31221)로 전환해 주세요.')).toBeInTheDocument()
  })

  it('확정된 MetaMask 트랜잭션의 해시와 블록 번호를 표시한다', () => {
    mocks.mintState.status = 'LINKED'
    mocks.mintState.hash = `0x${'a'.repeat(64)}`
    mocks.mintState.blockNumber = 123_456

    render(<MaterialMintPanel material={material} />)

    expect(screen.getByText('123,456')).toBeInTheDocument()
    expect(screen.getByTitle(`0x${'a'.repeat(64)}`)).toBeInTheDocument()
    expect(screen.getByText(/MetaMask 지갑이 직접 서명했습니다/)).toBeInTheDocument()
  })
})
