// 지갑 서명 로그인 전 흐름을 화면 단위로 검증한다.
//
// 모킹 경계는 **API 모듈 하나**다. window.ethereum 에 가짜 provider 를 꽂아 두면
// ethers 의 BrowserProvider → getSigner → personal_sign 이 실제로 돌기 때문에,
// walletService 는 모킹하지 않고 진짜로 통과시킨다. 그래야 "서명 대상 문자열이
// 서버가 준 정본과 같은가" 같은 계약을 실제 호출로 확인할 수 있다.
//
// vitest 설정에 globals 가 없어 testing-library 자동 cleanup 이 등록되지 않는다 —
// afterEach 에서 직접 부른다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { toUtf8String } from 'ethers'
import type { ReactElement } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../../../stores/authStore.ts'
import { useWalletStore } from '../../../stores/walletStore.ts'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { EthereumProvider } from '../types/wallet.types.ts'
import { buildLoginMessage } from '../hooks/useWalletLogin.ts'
import { WalletLoginButton } from './WalletLoginButton.tsx'

// vi.hoisted 없이 쓰면 팩토리 호이스팅 때문에 초기화 전 참조 에러가 난다.
const api = vi.hoisted(() => ({
  requestWalletNonce: vi.fn(),
  verifyWalletSignature: vi.fn(),
}))
vi.mock('../../auth/api/walletAuthApi.ts', () => api)

const LOWER = '0xd8da6bf26964af9d7eed9e03e53415d37aa96045'
const CHECKSUM = '0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045'
const NONCE = '0123456789abcdef0123456789abcdef'
const SIGNATURE = '0x' + 'ab'.repeat(65)
const CHAIN_HEX = '0x539'
const CHAIN_DECIMAL = 1337

const USER = {
  id: 7,
  email: 'supplier@example.com',
  name: '공급자',
  phone: '010-0000-0000',
  role: 'SUPPLIER' as const,
  status: 'ACTIVE' as const,
  createdAt: '2026-09-04T03:00:00',
}

type Handler = (...args: never[]) => void
type Request = { method: string; params?: unknown[] }

type FakeProvider = EthereumProvider & {
  calls: string[]
  requests: Request[]
}

/** 응답 표대로 답하는 가짜 provider. 값이 Error 면 reject, 함수면 호출한다. */
function fakeProvider(responses: Record<string, unknown> = {}): FakeProvider {
  const handlers = new Map<string, Set<Handler>>()
  const calls: string[] = []
  const requests: Request[] = []
  return {
    isMetaMask: true,
    calls,
    requests,
    async request({ method, params }) {
      calls.push(method)
      requests.push({ method, params })
      const value = responses[method]
      if (value instanceof Error) throw value
      if (typeof value === 'function') return (value as (p?: unknown[]) => unknown)(params)
      return value
    },
    on(event, handler) {
      if (!handlers.has(event)) handlers.set(event, new Set())
      handlers.get(event)!.add(handler)
    },
    removeListener(event, handler) {
      handlers.get(event)?.delete(handler)
    },
  }
}

function rpcError(code: number): Error {
  return Object.assign(new Error(`rpc ${code}`), { code })
}

/** 연결된 지갑 하나가 꽂혀 있는 상태. */
function connectedProvider(overrides: Record<string, unknown> = {}): FakeProvider {
  return fakeProvider({
    eth_accounts: [LOWER],
    eth_requestAccounts: [LOWER],
    eth_chainId: CHAIN_HEX,
    personal_sign: SIGNATURE,
    ...overrides,
  })
}

function installProvider(provider: FakeProvider | null): void {
  if (provider) {
    window.ethereum = provider
  } else {
    delete window.ethereum
  }
}

/** QueryClientProvider 를 쓰는 첫 테스트다. 재시도를 끄지 않으면 실패 케이스가 느려진다. */
function renderWithClient(ui: ReactElement) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>)
}

/** WalletButton 없이도 게이팅을 통과시키려고 스토어를 직접 채운다. */
function markConnected() {
  useWalletStore.getState().setWallet({ address: CHECKSUM, chainId: CHAIN_HEX })
}

function nonceResponse(overrides: Record<string, unknown> = {}) {
  return {
    walletAddress: CHECKSUM,
    nonce: NONCE,
    message: buildLoginMessage(CHECKSUM, NONCE),
    expiresAt: '2026-09-04T03:05:00',
    ...overrides,
  }
}

beforeEach(() => {
  installProvider(null)
  useWalletStore.getState().clear()
  useAuthStore.getState().signOut()
  // authStore 는 persist 를 쓴다. 지우지 않으면 앞 테스트의 세션이 되살아난다.
  localStorage.clear()
  // clearAllMocks 는 호출 기록만 지운다. 구현까지 지워야 앞 테스트의
  // mockResolvedValue 가 남아 엉뚱한 경로를 태우지 않는다.
  vi.resetAllMocks()
})

afterEach(() => {
  cleanup()
  installProvider(null)
})

describe('연결 전 게이팅', () => {
  it('지갑이 연결되어 있지 않으면 로그인 버튼 대신 안내만 보여준다', () => {
    renderWithClient(<WalletLoginButton />)

    expect(screen.getByText('지갑을 연결하면 서명으로 로그인할 수 있습니다.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '지갑으로 로그인' })).not.toBeInTheDocument()
  })
})

describe('성공 흐름', () => {
  it('nonce 요청 → 서명 → verify → 세션까지 이어진다', async () => {
    const provider = connectedProvider()
    installProvider(provider)
    markConnected()
    api.requestWalletNonce.mockResolvedValue(nonceResponse())
    api.verifyWalletSignature.mockResolvedValue({ accessToken: 'jwt', user: USER })
    const onSuccess = vi.fn()

    renderWithClient(<WalletLoginButton onSuccess={onSuccess} />)
    await userEvent.click(screen.getByRole('button', { name: '지갑으로 로그인' }))

    // 흐름의 마지막 단계까지 끝난 뒤에 중간 단계를 되짚는다. 중간에서 단언하면
    // 아직 도달하지 않은 단계를 보게 되고, 실패로 테스트가 끊기면 남은 비동기 흐름이
    // 다음 테스트로 새어 들어간다.
    await waitFor(() =>
      expect(api.verifyWalletSignature).toHaveBeenCalledWith({
        walletAddress: CHECKSUM,
        nonce: NONCE,
        signature: SIGNATURE,
      }),
    )

    // 1) nonce 요청 — 체크섬 주소와 10진수 체인 ID
    expect(api.requestWalletNonce).toHaveBeenCalledWith({
      walletAddress: CHECKSUM,
      chainId: CHAIN_DECIMAL,
    })

    // 2) 실제로 서명한 문자열이 서버가 준 정본과 같은가
    const signRequest = provider.requests.find((request) => request.method === 'personal_sign')
    expect(signRequest).toBeDefined()
    expect(toUtf8String(signRequest!.params![0] as string)).toBe(
      buildLoginMessage(CHECKSUM, NONCE),
    )

    // 4) 세션이 서고 지갑 주소까지 채워진다 (이메일 로그인과 다른 점)
    await waitFor(() => {
      const state = useAuthStore.getState()
      expect(state.isAuthenticated).toBe(true)
      expect(state.accessToken).toBe('jwt')
      expect(state.user?.walletAddress).toBe(CHECKSUM)
      expect(state.user?.id).toBe(USER.id)
    })
    expect(onSuccess).toHaveBeenCalledTimes(1)
  })

  it('진행 중에는 버튼이 비활성화된다', async () => {
    installProvider(connectedProvider())
    markConnected()
    let release: ((value: unknown) => void) | undefined
    api.requestWalletNonce.mockReturnValue(
      new Promise((resolve) => {
        release = resolve
      }),
    )
    api.verifyWalletSignature.mockResolvedValue({ accessToken: 'jwt', user: USER })

    renderWithClient(<WalletLoginButton />)
    await userEvent.click(screen.getByRole('button', { name: '지갑으로 로그인' }))

    const pending = await screen.findByRole('button', { name: '지갑 서명 요청 중…' })
    expect(pending).toBeDisabled()

    release!(nonceResponse())
    await waitFor(() => expect(useAuthStore.getState().isAuthenticated).toBe(true))
  })
})

describe('실패 흐름', () => {
  it('미등록 지갑이면 서명 팝업 없이 안내로 끝난다', async () => {
    const provider = connectedProvider()
    installProvider(provider)
    markConnected()
    api.requestWalletNonce.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, {
        code: 'WALLET_NOT_LINKED',
        status: 404,
        message: '등록되지 않은 지갑 주소입니다.',
      }),
    )

    renderWithClient(<WalletLoginButton />)
    await userEvent.click(screen.getByRole('button', { name: '지갑으로 로그인' }))

    expect(
      await screen.findByText(
        '이 지갑에 연결된 계정이 없습니다. 이메일로 로그인한 뒤 지갑을 연결해 주세요.',
      ),
    ).toBeInTheDocument()
    // 서명 팝업 전에 걸러진다는 것이 이 정책의 요점이다.
    expect(provider.calls).not.toContain('personal_sign')
    expect(api.verifyWalletSignature).not.toHaveBeenCalled()
    expect(useAuthStore.getState().isAuthenticated).toBe(false)
  })

  it('사용자가 서명을 취소하면 verify 를 부르지 않고 조용히 안내한다', async () => {
    installProvider(connectedProvider({ personal_sign: rpcError(4001) }))
    markConnected()
    api.requestWalletNonce.mockResolvedValue(nonceResponse())

    renderWithClient(<WalletLoginButton />)
    await userEvent.click(screen.getByRole('button', { name: '지갑으로 로그인' }))

    expect(await screen.findByText('서명을 취소했습니다.')).toBeInTheDocument()
    // 오류가 아니라 정상 흐름이므로 role="alert" 로 띄우지 않는다.
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(api.verifyWalletSignature).not.toHaveBeenCalled()
  })

  it('nonce 가 만료되면 재시도를 안내한다', async () => {
    installProvider(connectedProvider())
    markConnected()
    api.requestWalletNonce.mockResolvedValue(nonceResponse())
    api.verifyWalletSignature.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, {
        code: 'NONCE_EXPIRED',
        status: 400,
        message: '인증 요청이 만료되었습니다. 다시 시도해 주세요.',
      }),
    )

    renderWithClient(<WalletLoginButton />)
    await userEvent.click(screen.getByRole('button', { name: '지갑으로 로그인' }))

    expect(await screen.findByText('인증 유효 시간이 지났습니다. 다시 시도해 주세요.')).toBeInTheDocument()
    expect(useAuthStore.getState().isAuthenticated).toBe(false)
  })

  it('매핑에 없는 코드는 서버 문구를 그대로 보여준다', async () => {
    installProvider(connectedProvider())
    markConnected()
    api.requestWalletNonce.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, {
        code: 'WALLET_CHAIN_MISMATCH',
        status: 400,
        message: '지갑에 연결된 네트워크가 등록된 네트워크와 다릅니다.',
      }),
    )

    renderWithClient(<WalletLoginButton />)
    await userEvent.click(screen.getByRole('button', { name: '지갑으로 로그인' }))

    expect(
      await screen.findByText('지갑에 연결된 네트워크가 등록된 네트워크와 다릅니다.'),
    ).toBeInTheDocument()
  })

  it('네트워크 오류는 기본 문구로 안내한다', async () => {
    installProvider(connectedProvider())
    markConnected()
    api.requestWalletNonce.mockRejectedValue(createAppError(ERROR_KIND.NETWORK_ERROR))

    renderWithClient(<WalletLoginButton />)
    await userEvent.click(screen.getByRole('button', { name: '지갑으로 로그인' }))

    expect(await screen.findByRole('alert')).toBeInTheDocument()
  })

  it('서버가 다른 형식의 메시지를 주면 서명하지 않는다', async () => {
    // 브랜치 간 계약이 어긋난 상황. 사용자가 알 수 없는 문자열에 서명하게 두지 않는다.
    const provider = connectedProvider()
    installProvider(provider)
    markConnected()
    api.requestWalletNonce.mockResolvedValue(nonceResponse({ message: '이상한 메시지' }))

    renderWithClient(<WalletLoginButton />)
    await userEvent.click(screen.getByRole('button', { name: '지갑으로 로그인' }))

    expect(
      await screen.findByText('서명 요청 형식이 서버와 일치하지 않습니다. 관리자에게 문의해 주세요.'),
    ).toBeInTheDocument()
    expect(provider.calls).not.toContain('personal_sign')
  })

  it('다시 누르면 이전 오류 문구가 사라진다', async () => {
    installProvider(connectedProvider())
    markConnected()
    api.requestWalletNonce.mockRejectedValueOnce(
      createAppError(ERROR_KIND.API_ERROR, { code: 'NONCE_EXPIRED', message: '만료' }),
    )
    api.requestWalletNonce.mockResolvedValue(nonceResponse())
    api.verifyWalletSignature.mockResolvedValue({ accessToken: 'jwt', user: USER })

    renderWithClient(<WalletLoginButton />)
    await userEvent.click(screen.getByRole('button', { name: '지갑으로 로그인' }))
    await screen.findByText('인증 유효 시간이 지났습니다. 다시 시도해 주세요.')

    await userEvent.click(screen.getByRole('button', { name: '지갑으로 로그인' }))

    await waitFor(() =>
      expect(
        screen.queryByText('인증 유효 시간이 지났습니다. 다시 시도해 주세요.'),
      ).not.toBeInTheDocument(),
    )
  })
})
