// Wallet 연결 기능을 화면 단위로 고정하는 테스트.
//
// walletClient.test.ts 는 로직 계층(EIP-1193 호출과 상태 전이)만 본다.
// 여기서는 그 상태가 실제로 화면에 옮겨지는지를 본다.
//
// describe 는 테스트 항목 6개와 1:1 로 대응시킨다:
//   1. MetaMask 설치 상태   2. MetaMask 미설치 상태   3. 연결 승인
//   4. 연결 거절            5. Wallet Account 변경    6. Network 변경
//
// window.ethereum 을 가짜 provider 로 갈아끼우는 방식을 쓴다. useWallet 이
// walletService.getInjectedProvider() 를 통해 이걸 읽으므로 모듈 모킹이 필요 없다.

import { act, cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useWalletStore } from '../../../stores/walletStore.ts'
import type { EthereumProvider } from '../types/wallet.types.ts'
import { WalletButton } from './WalletButton.tsx'

// ── env 모킹 ────────────────────────────────────────────────
// config/env.ts 는 import.meta.env 를 모듈 로드 시점에 한 번 읽어 상수로 굳힌다.
// 네트워크 검증 UI(wrongChain)는 VITE_BESU_CHAIN_ID 가 있어야 켜지는데,
// 테스트 환경에는 그 값이 없어 기본 상태로는 항목 6을 검증할 수 없다.
// vi.hoisted 로 만든 가변 객체를 env 로 끼워 넣어 케이스마다 값을 바꾼다.
const envState = vi.hoisted(() => ({
  apiBaseUrl: '/api',
  besuRpcUrl: null as string | null,
  besuChainIdHex: null as string | null,
}))

vi.mock('../../../config/env.ts', () => ({
  env: envState,
  toChainIdHex: (raw: string | null) => raw,
}))

const LOWER = '0xd8da6bf26964af9d7eed9e03e53415d37aa96045'
const CHECKSUM = '0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045'
const SHORT = '0xd8dA…6045'
const OTHER = '0x70997970C51812dc3A010C7d01b50e0d17dc79C8'
const OTHER_SHORT = '0x7099…79C8'

/** SSAFY Besu 를 가정한 기대 체인. 실제 값이 정해지면 이 상수만 바꾸면 된다. */
const EXPECTED_CHAIN = '0x539'
const OTHER_CHAIN = '0x1'
const RPC_URL = 'http://besu.example:8545'

type Handler = (...args: never[]) => void

type FakeProvider = EthereumProvider & {
  calls: string[]
  emit: (event: string, payload: unknown) => void
}

/** 응답 표대로 답하는 가짜 provider. 값이 Error 면 reject 한다. */
function fakeProvider(responses: Record<string, unknown> = {}): FakeProvider {
  const handlers = new Map<string, Set<Handler>>()
  const calls: string[] = []
  return {
    isMetaMask: true,
    calls,
    async request({ method }) {
      calls.push(method)
      const value = responses[method]
      if (value instanceof Error) throw value
      return value
    },
    on(event, handler) {
      if (!handlers.has(event)) handlers.set(event, new Set())
      handlers.get(event)!.add(handler)
    },
    removeListener(event, handler) {
      handlers.get(event)?.delete(handler)
    },
    emit(event, payload) {
      for (const handler of handlers.get(event) ?? []) {
        const call = handler as unknown as (payload: unknown) => void
        call(payload)
      }
    },
  }
}

/** 이미 승인된 계정이 있는 상태의 provider (새로고침 복원 시나리오) */
function connectedProvider(
  overrides: Record<string, unknown> = {},
  chainId: string = EXPECTED_CHAIN,
): FakeProvider {
  return fakeProvider({ eth_accounts: [LOWER], eth_chainId: chainId, ...overrides })
}

function rpcError(code: number): Error {
  return Object.assign(new Error(`rpc ${code}`), { code })
}

/** window.ethereum 을 갈아끼운다. 지우려면 null 을 넘긴다. */
function installProvider(provider: FakeProvider | null): void {
  if (provider) {
    window.ethereum = provider
  } else {
    delete window.ethereum
  }
}

/** 연결된 상태로 렌더한 뒤 주소가 뜰 때까지 기다린다. */
async function renderConnected(provider: FakeProvider): Promise<void> {
  installProvider(provider)
  render(<WalletButton />)
  await screen.findByText(SHORT)
}

beforeEach(() => {
  installProvider(null)
  // 스토어와 env 는 모듈 전역이라 테스트 간에 새어 나간다. 매번 되돌린다.
  useWalletStore.getState().clear()
  envState.besuChainIdHex = null
  envState.besuRpcUrl = null
})

afterEach(() => {
  // vitest 설정에 globals 가 없어 testing-library 의 자동 cleanup 이 등록되지 않는다.
  // 직접 정리하지 않으면 이전 테스트의 DOM 이 남아 "Found multiple elements" 가 난다.
  cleanup()
  installProvider(null)
})

// ────────────────────────────────────────────────────────────
describe('1. MetaMask 설치 상태', () => {
  it('확장 프로그램이 감지되면 연결 버튼을 보여주고 설치 안내는 띄우지 않는다', async () => {
    installProvider(fakeProvider({ eth_accounts: [], eth_chainId: EXPECTED_CHAIN }))

    render(<WalletButton />)

    expect(await screen.findByRole('button', { name: '지갑 연결' })).toBeEnabled()
    expect(screen.queryByRole('link', { name: 'MetaMask 설치하기' })).not.toBeInTheDocument()
    expect(
      screen.queryByText('지갑 확장 프로그램이 감지되지 않았습니다.'),
    ).not.toBeInTheDocument()
  })

  it('isMetaMask 플래그가 없는 호환 지갑도 그대로 연결된다', async () => {
    const provider = fakeProvider({
      eth_accounts: [],
      eth_requestAccounts: [LOWER],
      eth_chainId: EXPECTED_CHAIN,
    })
    // MetaMask 전용 분기를 두지 않았음을 고정한다 (Rabby/Brave 등도 EIP-1193 이면 동작).
    provider.isMetaMask = undefined
    installProvider(provider)

    render(<WalletButton />)
    await userEvent.click(await screen.findByRole('button', { name: '지갑 연결' }))

    expect(await screen.findByText(SHORT)).toBeInTheDocument()
  })
})

// ────────────────────────────────────────────────────────────
describe('2. MetaMask 미설치 상태', () => {
  it('window.ethereum 이 없으면 설치 안내와 다운로드 링크를 보여준다', () => {
    render(<WalletButton />)

    expect(screen.getByText('지갑 확장 프로그램이 감지되지 않았습니다.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'MetaMask 설치하기' })).toHaveAttribute(
      'href',
      'https://metamask.io/download/',
    )
    expect(screen.queryByRole('button', { name: '지갑 연결' })).not.toBeInTheDocument()
  })

  it('설치 링크는 새 탭으로 열되 opener 를 넘기지 않는다', () => {
    render(<WalletButton />)

    const link = screen.getByRole('link', { name: 'MetaMask 설치하기' })
    expect(link).toHaveAttribute('target', '_blank')
    expect(link.getAttribute('rel')).toContain('noopener')
  })
})

// ────────────────────────────────────────────────────────────
describe('3. 연결 승인', () => {
  it('버튼을 누르면 eth_requestAccounts 로 연결을 요청하고 주소를 표시한다', async () => {
    const provider = fakeProvider({
      eth_accounts: [],
      eth_requestAccounts: [LOWER],
      eth_chainId: EXPECTED_CHAIN,
    })
    installProvider(provider)

    render(<WalletButton />)
    await userEvent.click(await screen.findByRole('button', { name: '지갑 연결' }))

    await waitFor(() => expect(provider.calls).toContain('eth_requestAccounts'))
    expect(await screen.findByText(SHORT)).toBeInTheDocument()
  })

  it('연결 요청이 진행되는 동안 버튼이 비활성화된다 (중복 팝업 방지)', async () => {
    let release: ((accounts: string[]) => void) | undefined
    const pending = new Promise<string[]>((resolve) => {
      release = resolve
    })
    const provider = fakeProvider({ eth_accounts: [], eth_chainId: EXPECTED_CHAIN })
    // eth_requestAccounts 만 수동으로 붙잡아 '연결하는 중' 상태를 관찰한다.
    const original = provider.request.bind(provider)
    provider.request = async (args) =>
      args.method === 'eth_requestAccounts' ? pending : original(args)
    installProvider(provider)

    render(<WalletButton />)
    await userEvent.click(await screen.findByRole('button', { name: '지갑 연결' }))

    expect(await screen.findByRole('button', { name: '연결하는 중…' })).toBeDisabled()

    await act(async () => {
      release!([LOWER])
    })
    expect(await screen.findByText(SHORT)).toBeInTheDocument()
  })

  it('이미 승인된 계정이 있으면 새로고침만으로 주소가 보인다 (팝업 없음)', async () => {
    const provider = connectedProvider()
    await renderConnected(provider)

    expect(provider.calls).toContain('eth_accounts')
    expect(provider.calls).not.toContain('eth_requestAccounts')
  })

  it('축약해 보여주되 전체 주소를 title 로 남긴다 (EIP-55 체크섬)', async () => {
    await renderConnected(connectedProvider())

    expect(await screen.findByTitle(CHECKSUM)).toHaveTextContent(SHORT)
  })

  it('전역 스토어에도 같은 주소가 반영된다', async () => {
    await renderConnected(connectedProvider())

    await waitFor(() => {
      expect(useWalletStore.getState().address).toBe(CHECKSUM)
      expect(useWalletStore.getState().isConnected).toBe(true)
    })
  })
})

// ────────────────────────────────────────────────────────────
describe('4. 연결 거절', () => {
  it('사용자가 팝업에서 취소하면(4001) 예외 없이 안내만 남는다', async () => {
    installProvider(
      fakeProvider({
        eth_accounts: [],
        eth_requestAccounts: rpcError(4001),
        eth_chainId: EXPECTED_CHAIN,
      }),
    )

    render(<WalletButton />)
    await userEvent.click(await screen.findByRole('button', { name: '지갑 연결' }))

    expect(await screen.findByText('연결을 취소했습니다.')).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: '지갑 연결' })).toBeEnabled()
  })

  it('팝업이 이미 떠 있으면(-32002) 지갑을 확인하라고 안내한다', async () => {
    installProvider(
      fakeProvider({
        eth_accounts: [],
        eth_requestAccounts: rpcError(-32002),
        eth_chainId: EXPECTED_CHAIN,
      }),
    )

    render(<WalletButton />)
    await userEvent.click(await screen.findByRole('button', { name: '지갑 연결' }))

    expect(await screen.findByText('MetaMask 창에서 요청을 확인해 주세요.')).toBeInTheDocument()
  })

  it('계정 없이 빈 배열만 돌아오면 실패로 안내한다', async () => {
    installProvider(
      fakeProvider({ eth_accounts: [], eth_requestAccounts: [], eth_chainId: EXPECTED_CHAIN }),
    )

    render(<WalletButton />)
    await userEvent.click(await screen.findByRole('button', { name: '지갑 연결' }))

    expect(
      await screen.findByText('지갑에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.'),
    ).toBeInTheDocument()
  })

  it('연결 해제를 누르면 주소가 사라지고 연결 버튼으로 돌아간다', async () => {
    await renderConnected(connectedProvider())

    await userEvent.click(screen.getByRole('button', { name: '연결 해제' }))

    expect(await screen.findByRole('button', { name: '지갑 연결' })).toBeInTheDocument()
    expect(screen.queryByText(SHORT)).not.toBeInTheDocument()
  })
})

// ────────────────────────────────────────────────────────────
describe('5. Wallet Account 변경', () => {
  it('accountsChanged 로 다른 계정이 오면 화면 주소가 바뀐다', async () => {
    const provider = connectedProvider()
    await renderConnected(provider)

    act(() => provider.emit('accountsChanged', [OTHER]))

    expect(await screen.findByText(OTHER_SHORT)).toBeInTheDocument()
    expect(screen.queryByText(SHORT)).not.toBeInTheDocument()
  })

  it('계정이 바뀌면 전역 스토어도 따라 갱신된다', async () => {
    const provider = connectedProvider()
    await renderConnected(provider)

    act(() => provider.emit('accountsChanged', [OTHER]))

    await waitFor(() => expect(useWalletStore.getState().address).toBe(OTHER))
  })

  it('빈 배열이 오면 = 사용자가 사이트 연결을 끊은 것이므로 연결 화면으로 돌아간다', async () => {
    const provider = connectedProvider()
    await renderConnected(provider)

    act(() => provider.emit('accountsChanged', []))

    expect(await screen.findByRole('button', { name: '지갑 연결' })).toBeInTheDocument()
    expect(screen.queryByText(SHORT)).not.toBeInTheDocument()
    await waitFor(() => expect(useWalletStore.getState().isConnected).toBe(false))
  })
})

// ────────────────────────────────────────────────────────────
describe('6. Network 변경', () => {
  it('chainChanged 가 오면 표시 중인 네트워크가 갱신된다', async () => {
    const provider = connectedProvider()
    await renderConnected(provider)
    // '네트워크 {chainId}' 는 JSX 상 텍스트 노드가 둘로 쪼개져 있다.
    // getByText(chainId) 로는 잡히지 않으므로 문단을 집어 textContent 로 본다.
    const networkNote = () => screen.getByText(/^지갑 연결 환경/)
    expect(networkNote()).toHaveTextContent(EXPECTED_CHAIN)

    act(() => provider.emit('chainChanged', OTHER_CHAIN))

    await waitFor(() => expect(networkNote()).toHaveTextContent(OTHER_CHAIN))
    expect(networkNote()).not.toHaveTextContent(EXPECTED_CHAIN)
  })

  it('기대 체인이 설정돼 있고 다른 네트워크면 경고와 전환 버튼이 뜬다', async () => {
    envState.besuChainIdHex = EXPECTED_CHAIN
    await renderConnected(connectedProvider({}, OTHER_CHAIN))

    expect(await screen.findByText(new RegExp(`${EXPECTED_CHAIN} 이어야 합니다`))).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '연결 환경 변경' })).toBeInTheDocument()
  })

  it('기대 체인이 설정되지 않았으면(현 배포 상태) 경고를 띄우지 않는다', async () => {
    // VITE_BESU_CHAIN_ID 는 Blockchain 파트 산출물이 나오기 전까지 비어 있다.
    // 그 동안 사용자에게 잘못된 경고를 보이면 안 된다.
    envState.besuChainIdHex = null
    await renderConnected(connectedProvider({}, OTHER_CHAIN))

    expect(screen.queryByRole('button', { name: '연결 환경 변경' })).not.toBeInTheDocument()
    expect(screen.queryByText(/이어야 합니다/)).not.toBeInTheDocument()
  })

  it('전환 버튼을 누르면 wallet_switchEthereumChain 을 호출한다', async () => {
    envState.besuChainIdHex = EXPECTED_CHAIN
    const provider = connectedProvider({ wallet_switchEthereumChain: null }, OTHER_CHAIN)
    await renderConnected(provider)

    await userEvent.click(screen.getByRole('button', { name: '연결 환경 변경' }))

    await waitFor(() => expect(provider.calls).toContain('wallet_switchEthereumChain'))
    expect(provider.calls).not.toContain('wallet_addEthereumChain')
  })

  it('지갑에 없는 체인(4902)이면 RPC URL 로 등록을 시도한다', async () => {
    envState.besuChainIdHex = EXPECTED_CHAIN
    envState.besuRpcUrl = RPC_URL
    const provider = connectedProvider(
      { wallet_switchEthereumChain: rpcError(4902), wallet_addEthereumChain: null },
      OTHER_CHAIN,
    )
    await renderConnected(provider)

    await userEvent.click(screen.getByRole('button', { name: '연결 환경 변경' }))

    await waitFor(() => expect(provider.calls).toContain('wallet_addEthereumChain'))
  })

  it('등록이 필요한데 RPC URL 이 없으면 시도조차 하지 않고 설정 미비를 알린다', async () => {
    envState.besuChainIdHex = EXPECTED_CHAIN
    envState.besuRpcUrl = null
    const provider = connectedProvider(
      { wallet_switchEthereumChain: rpcError(4902) },
      OTHER_CHAIN,
    )
    await renderConnected(provider)

    await userEvent.click(screen.getByRole('button', { name: '연결 환경 변경' }))

    expect(
      await screen.findByText('지갑 연결 환경이 아직 설정되지 않았습니다. 관리자에게 문의하세요.'),
    ).toBeInTheDocument()
    expect(provider.calls).not.toContain('wallet_addEthereumChain')
  })

  it('사용자가 네트워크 전환을 거부하면 취소 안내를 남긴다', async () => {
    envState.besuChainIdHex = EXPECTED_CHAIN
    const provider = connectedProvider(
      { wallet_switchEthereumChain: rpcError(4001) },
      OTHER_CHAIN,
    )
    await renderConnected(provider)

    await userEvent.click(screen.getByRole('button', { name: '연결 환경 변경' }))

    expect(await screen.findByText('연결 환경 변경을 취소했습니다.')).toBeInTheDocument()
  })

  it('전환에 성공하면 chainChanged 반영 후 경고가 사라진다', async () => {
    envState.besuChainIdHex = EXPECTED_CHAIN
    const provider = connectedProvider({ wallet_switchEthereumChain: null }, OTHER_CHAIN)
    await renderConnected(provider)
    await userEvent.click(screen.getByRole('button', { name: '연결 환경 변경' }))

    // 실제 지갑은 전환이 끝나면 chainChanged 를 밀어준다. 그 흐름을 재현한다.
    act(() => provider.emit('chainChanged', EXPECTED_CHAIN))

    await waitFor(() =>
      expect(screen.queryByRole('button', { name: '연결 환경 변경' })).not.toBeInTheDocument(),
    )
    expect(screen.queryByText(/이어야 합니다/)).not.toBeInTheDocument()
  })
})
