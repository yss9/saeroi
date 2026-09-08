import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, renderHook } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../../../stores/authStore.ts'
import {
  registerWallet,
  requestWalletRegistrationNonce,
} from '../api/walletRegistrationApi.ts'
import { buildLoginMessage } from '../services/walletMessage.ts'
import { getChainIdNumber, getWalletAddress, signMessage } from '../services/walletService.ts'
import { useWalletRegistration } from './useWalletRegistration.ts'

vi.mock('../api/walletRegistrationApi.ts', () => ({
  requestWalletRegistrationNonce: vi.fn(),
  registerWallet: vi.fn(),
}))

vi.mock('../services/walletService.ts', () => ({
  getWalletAddress: vi.fn(),
  getChainIdNumber: vi.fn(),
  signMessage: vi.fn(),
}))

const ADDRESS = '0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045'
const NONCE = '0123456789abcdef0123456789abcdef'
const SIGNATURE = `0x${'ab'.repeat(65)}`

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  }
}

beforeEach(() => {
  useAuthStore.setState({
    user: {
      id: 7,
      email: 'brand@saeroi.test',
      name: '새로이 브랜드',
      role: 'BRAND',
      walletAddress: null,
    },
    accessToken: 'access-token',
    isAuthenticated: true,
  })
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
})

describe('useWalletRegistration', () => {
  it('requests a challenge, signs it, registers the wallet, and updates the session user', async () => {
    const message = buildLoginMessage(ADDRESS, NONCE)
    vi.mocked(getWalletAddress).mockResolvedValue(ADDRESS)
    vi.mocked(getChainIdNumber).mockResolvedValue(1337)
    vi.mocked(requestWalletRegistrationNonce).mockResolvedValue({
      walletAddress: ADDRESS,
      nonce: NONCE,
      message,
      expiresAt: '2026-09-07T12:05:00',
    })
    vi.mocked(signMessage).mockResolvedValue(SIGNATURE)
    vi.mocked(registerWallet).mockResolvedValue({
      id: 3,
      walletAddress: ADDRESS,
      chainId: 1337,
      active: true,
      verified: true,
      verifiedAt: '2026-09-07T12:00:00',
      createdAt: '2026-09-07T12:00:00',
      deactivatedAt: null,
    })
    const { result } = renderHook(() => useWalletRegistration(), { wrapper: createWrapper() })

    await act(async () => {
      await result.current.mutateAsync()
    })

    expect(requestWalletRegistrationNonce).toHaveBeenCalledWith({
      walletAddress: ADDRESS,
      chainId: 1337,
    })
    expect(signMessage).toHaveBeenCalledWith(message)
    expect(registerWallet).toHaveBeenCalledWith({
      walletAddress: ADDRESS,
      chainId: 1337,
      nonce: NONCE,
      signature: SIGNATURE,
    })
    expect(useAuthStore.getState().user?.walletAddress).toBe(ADDRESS)
  })

  it('does not open the signing request when the server message differs', async () => {
    vi.mocked(getWalletAddress).mockResolvedValue(ADDRESS)
    vi.mocked(getChainIdNumber).mockResolvedValue(1337)
    vi.mocked(requestWalletRegistrationNonce).mockResolvedValue({
      walletAddress: ADDRESS,
      nonce: NONCE,
      message: 'unexpected message',
      expiresAt: '2026-09-07T12:05:00',
    })
    const { result } = renderHook(() => useWalletRegistration(), { wrapper: createWrapper() })

    await expect(result.current.mutateAsync()).rejects.toMatchObject({
      message: '서명 요청 형식이 서버와 일치하지 않습니다. 관리자에게 문의해 주세요.',
    })

    expect(signMessage).not.toHaveBeenCalled()
    expect(registerWallet).not.toHaveBeenCalled()
  })
})
