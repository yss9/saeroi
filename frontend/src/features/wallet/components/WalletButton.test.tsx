// 완료 조건을 화면 단위로 고정하는 테스트.
//
// walletClient.test.ts 는 로직 계층(EIP-1193 호출과 상태 전이)만 본다.
// 여기서는 그 상태가 실제로 화면에 옮겨지는지를 본다 — 버튼을 눌러 연결되는가,
// 주소가 보이는가, 계정을 바꾸면 화면이 따라오는가, 미설치 안내가 뜨는가.
//
// window.ethereum 을 가짜 provider 로 갈아끼우는 방식을 쓴다. useWallet 이
// walletService.getInjectedProvider() 를 통해 이걸 읽으므로 모듈 모킹이 필요 없다.

import { act, cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { useWalletStore } from '../../../stores/walletStore.ts'
import type { EthereumProvider } from '../types/wallet.types.ts'
import { WalletButton } from './WalletButton.tsx'

const LOWER = '0xd8da6bf26964af9d7eed9e03e53415d37aa96045'
const CHECKSUM = '0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045'
const SHORT = '0xd8dA…6045'
const OTHER = '0x70997970C51812dc3A010C7d01b50e0d17dc79C8'
const OTHER_SHORT = '0x7099…79C8'

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

beforeEach(() => {
  installProvider(null)
  // 스토어는 모듈 전역이라 테스트 간에 새어 나간다. 매번 비운다.
  useWalletStore.getState().clear()
})

afterEach(() => {
  // vitest 설정에 globals 가 없어 testing-library 의 자동 cleanup 이 등록되지 않는다.
  // 직접 정리하지 않으면 이전 테스트의 DOM 이 남아 "Found multiple elements" 가 난다.
  cleanup()
  installProvider(null)
})

describe('완료 조건 1 — 연결 버튼으로 Wallet 연결', () => {
  it('버튼을 누르면 eth_requestAccounts 로 연결을 요청한다', async () => {
    const provider = fakeProvider({
      eth_accounts: [],
      eth_requestAccounts: [LOWER],
      eth_chainId: '0x539',
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
    const provider = fakeProvider({ eth_accounts: [], eth_chainId: '0x539' })
    // eth_requestAccounts 만 수동으로 붙잡아 '연결하는 중' 상태를 관찰한다.
    const original = provider.request.bind(provider)
    provider.request = async (args) =>
      args.method === 'eth_requestAccounts' ? pending : original(args)
    installProvider(provider)

    render(<WalletButton />)
    await userEvent.click(await screen.findByRole('button', { name: '지갑 연결' }))

    const connecting = await screen.findByRole('button', { name: '연결하는 중…' })
    expect(connecting).toBeDisabled()

    await act(async () => {
      release!([LOWER])
    })
    expect(await screen.findByText(SHORT)).toBeInTheDocument()
  })
})

describe('완료 조건 2 — 현재 Wallet Address 화면 표시', () => {
  it('이미 승인된 계정이 있으면 새로고침만으로 주소가 보인다 (팝업 없음)', async () => {
    const provider = fakeProvider({ eth_accounts: [LOWER], eth_chainId: '0x539' })
    installProvider(provider)

    render(<WalletButton />)

    expect(await screen.findByText(SHORT)).toBeInTheDocument()
    expect(provider.calls).not.toContain('eth_requestAccounts')
  })

  it('축약해 보여주되 전체 주소를 title 로 남긴다 (체크섬 형태)', async () => {
    installProvider(fakeProvider({ eth_accounts: [LOWER], eth_chainId: '0x539' }))

    render(<WalletButton />)

    expect(await screen.findByTitle(CHECKSUM)).toHaveTextContent(SHORT)
  })

  it('전역 스토어에도 같은 주소가 반영된다', async () => {
    installProvider(fakeProvider({ eth_accounts: [LOWER], eth_chainId: '0x539' }))

    render(<WalletButton />)
    await screen.findByText(SHORT)

    await waitFor(() => {
      expect(useWalletStore.getState().address).toBe(CHECKSUM)
      expect(useWalletStore.getState().isConnected).toBe(true)
    })
  })
})

describe('완료 조건 3 — 계정 변경 시 화면 갱신', () => {
  it('accountsChanged 로 다른 계정이 오면 화면 주소가 바뀐다', async () => {
    const provider = fakeProvider({ eth_accounts: [LOWER], eth_chainId: '0x539' })
    installProvider(provider)

    render(<WalletButton />)
    await screen.findByText(SHORT)

    act(() => provider.emit('accountsChanged', [OTHER]))

    expect(await screen.findByText(OTHER_SHORT)).toBeInTheDocument()
    expect(screen.queryByText(SHORT)).not.toBeInTheDocument()
  })

  it('빈 배열이 오면 = 사용자가 사이트 연결을 끊은 것이므로 연결 화면으로 돌아간다', async () => {
    const provider = fakeProvider({ eth_accounts: [LOWER], eth_chainId: '0x539' })
    installProvider(provider)

    render(<WalletButton />)
    await screen.findByText(SHORT)

    act(() => provider.emit('accountsChanged', []))

    expect(await screen.findByRole('button', { name: '지갑 연결' })).toBeInTheDocument()
    expect(screen.queryByText(SHORT)).not.toBeInTheDocument()
  })
})

describe('완료 조건 4 — MetaMask 미설치 안내', () => {
  it('window.ethereum 이 없으면 설치 안내와 다운로드 링크를 보여준다', () => {
    render(<WalletButton />)

    expect(screen.getByText('지갑 확장 프로그램이 감지되지 않았습니다.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'MetaMask 설치하기' })).toHaveAttribute(
      'href',
      'https://metamask.io/download/',
    )
    expect(screen.queryByRole('button', { name: '지갑 연결' })).not.toBeInTheDocument()
  })
})

describe('부가 흐름', () => {
  it('사용자가 팝업에서 취소하면(4001) 예외 없이 안내만 남는다', async () => {
    installProvider(
      fakeProvider({
        eth_accounts: [],
        eth_requestAccounts: rpcError(4001),
        eth_chainId: '0x539',
      }),
    )

    render(<WalletButton />)
    await userEvent.click(await screen.findByRole('button', { name: '지갑 연결' }))

    expect(await screen.findByText('연결을 취소했습니다.')).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: '지갑 연결' })).toBeEnabled()
  })

  it('연결 해제를 누르면 주소가 사라지고 연결 버튼으로 돌아간다', async () => {
    installProvider(fakeProvider({ eth_accounts: [LOWER], eth_chainId: '0x539' }))

    render(<WalletButton />)
    await screen.findByText(SHORT)

    await userEvent.click(screen.getByRole('button', { name: '연결 해제' }))

    expect(await screen.findByRole('button', { name: '지갑 연결' })).toBeInTheDocument()
    expect(screen.queryByText(SHORT)).not.toBeInTheDocument()
  })
})
