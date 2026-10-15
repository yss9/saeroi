// 가짜 provider 를 끼워 MetaMask 연동 로직을 검증한다.
// 확장 프로그램도 브라우저(jsdom)도 필요 없다 — walletClient.ts 가 React·window 에
// 의존하지 않도록 설계했기 때문이다.

import { describe, expect, it, vi } from 'vitest'
import { shortenAddress } from '../../../utils/address.ts'
import { createWalletClient } from './walletClient.ts'
import type { EthereumProvider } from '../types/wallet.types.ts'

// 테스트 전반에서 쓰는 주소. 지갑이 소문자로 주는 상황을 재현하려고 일부러 소문자로 둔다.
const LOWER = '0xd8da6bf26964af9d7eed9e03e53415d37aa96045'
const CHECKSUM = '0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045'
const OTHER_CHECKSUM = '0x70997970C51812dc3A010C7d01b50e0d17dc79C8'

type Handler = (...args: never[]) => void

/**
 * 응답 표를 받아 그대로 돌려주는 가짜 provider.
 * 값이 Error 면 reject 한다. 등록된 이벤트 핸들러를 직접 호출할 수 있게 emit 을 노출한다.
 */
function fakeProvider(responses: Record<string, unknown> = {}) {
  const handlers = new Map<string, Set<Handler>>()
  const calls: string[] = []

  const provider: EthereumProvider & {
    calls: string[]
    emit: (event: string, payload: unknown) => void
    handlerCount: (event: string) => number
  } = {
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
    handlerCount: (event) => handlers.get(event)?.size ?? 0,
  }
  return provider
}

/** EIP-1193 규격의 에러 객체 (code 필드를 가진 Error) */
function rpcError(code: number): Error {
  return Object.assign(new Error(`rpc ${code}`), { code })
}

describe('확장 프로그램 유무', () => {
  it('provider 가 없으면 unavailable 이고 connect 해도 아무 일이 없다', async () => {
    const client = createWalletClient(null)
    expect(client.getState().status).toBe('unavailable')

    await client.connect()

    expect(client.getState().status).toBe('unavailable')
    expect(client.getState().address).toBeNull()
  })

  it('provider 가 있으면 disconnected 로 시작한다', () => {
    const client = createWalletClient(fakeProvider())
    expect(client.getState().status).toBe('disconnected')
  })
})

describe('restore — 팝업 없는 복원', () => {
  it('승인된 계정이 없으면 연결하지 않고, 팝업을 띄우는 요청도 보내지 않는다', async () => {
    const provider = fakeProvider({ eth_accounts: [] })
    const client = createWalletClient(provider)

    await client.restore()

    expect(client.getState().status).toBe('disconnected')
    expect(provider.calls).toEqual(['eth_accounts'])
    expect(provider.calls).not.toContain('eth_requestAccounts')
  })

  it('승인된 계정이 있으면 새로고침 후에도 연결 상태로 되돌아온다', async () => {
    const provider = fakeProvider({ eth_accounts: [LOWER], eth_chainId: '0x1' })
    const client = createWalletClient(provider)

    await client.restore()

    expect(client.getState()).toMatchObject({
      status: 'connected',
      address: CHECKSUM,
      chainId: '0x1',
    })
  })

  it('복원 중 오류는 조용히 삼킨다 (사용자가 요청한 동작이 아니므로)', async () => {
    const provider = fakeProvider({ eth_accounts: new Error('boom') })
    const client = createWalletClient(provider)

    await client.restore()

    expect(client.getState().status).toBe('disconnected')
    expect(client.getState().notice).toBeNull()
  })
})

describe('connect', () => {
  it('성공하면 체크섬 주소로 연결된다', async () => {
    const provider = fakeProvider({ eth_requestAccounts: [LOWER], eth_chainId: '0x539' })
    const client = createWalletClient(provider)

    await client.connect()

    expect(client.getState()).toMatchObject({
      status: 'connected',
      address: CHECKSUM,
      chainId: '0x539',
      notice: null,
    })
  })

  it('사용자가 거부하면(4001) 예외를 던지지 않고 rejected 안내만 남긴다', async () => {
    const provider = fakeProvider({ eth_requestAccounts: rpcError(4001) })
    const client = createWalletClient(provider)

    await expect(client.connect()).resolves.toBeUndefined()

    expect(client.getState()).toMatchObject({
      status: 'disconnected',
      address: null,
      notice: 'rejected',
    })
  })

  it('팝업이 떠 있는데 또 누르면 중복 요청을 만들지 않고 안내만 바꾼다', async () => {
    // eth_requestAccounts 를 수동으로 풀 수 있는 promise 로 잡아 "팝업이 떠 있는" 상태를 재현한다.
    let release: (accounts: string[]) => void = () => {}
    const pending = new Promise<string[]>((resolve) => {
      release = resolve
    })
    const provider = fakeProvider({ eth_chainId: '0x1' })
    provider.request = vi.fn(async ({ method }: { method: string }) => {
      provider.calls.push(method)
      if (method === 'eth_requestAccounts') return pending
      return '0x1'
    }) as EthereumProvider['request']

    const client = createWalletClient(provider)
    const first = client.connect()
    expect(client.getState().status).toBe('connecting')

    await client.connect() // 연타
    expect(client.getState().notice).toBe('pending-in-wallet')
    expect(provider.calls.filter((m) => m === 'eth_requestAccounts')).toHaveLength(1)

    release([LOWER])
    await first
    expect(client.getState().status).toBe('connected')
  })

  it('provider 가 -32002 를 던져도 pending-in-wallet 으로 분류한다', async () => {
    const provider = fakeProvider({ eth_requestAccounts: rpcError(-32002) })
    const client = createWalletClient(provider)

    await client.connect()

    expect(client.getState().notice).toBe('pending-in-wallet')
  })

  it('분류되지 않은 오류는 failed 로 남는다', async () => {
    const provider = fakeProvider({ eth_requestAccounts: new Error('network down') })
    const client = createWalletClient(provider)

    await client.connect()

    expect(client.getState().notice).toBe('failed')
  })

  it('체인 조회가 실패해도 연결 자체는 성공으로 본다', async () => {
    const provider = fakeProvider({
      eth_requestAccounts: [LOWER],
      eth_chainId: new Error('no chain'),
    })
    const client = createWalletClient(provider)

    await client.connect()

    expect(client.getState().status).toBe('connected')
    expect(client.getState().chainId).toBeNull()
  })
})

describe('지갑이 밀어주는 이벤트', () => {
  // 실제 사용에서는 React 가 항상 구독한 상태다. 구독이 있어야 리스너가 붙는다.
  it('계정을 바꾸면 주소가 따라 바뀐다', async () => {
    const provider = fakeProvider({ eth_requestAccounts: [LOWER], eth_chainId: '0x1' })
    const client = createWalletClient(provider)
    client.subscribe(() => {})
    await client.connect()

    provider.emit('accountsChanged', [OTHER_CHECKSUM])

    expect(client.getState().address).toBe(OTHER_CHECKSUM)
    expect(client.getState().status).toBe('connected')
  })

  it('빈 배열이 오면 = 사용자가 사이트 연결을 끊은 것이므로 disconnected 로 간다', async () => {
    const provider = fakeProvider({ eth_requestAccounts: [LOWER], eth_chainId: '0x1' })
    const client = createWalletClient(provider)
    client.subscribe(() => {})
    await client.connect()

    provider.emit('accountsChanged', [])

    expect(client.getState()).toMatchObject({ status: 'disconnected', address: null })
  })

  it('네트워크를 바꾸면 chainId 가 갱신된다', () => {
    const provider = fakeProvider()
    const client = createWalletClient(provider)
    client.subscribe(() => {})

    provider.emit('chainChanged', '0x7a69')

    expect(client.getState().chainId).toBe('0x7a69')
  })

  it('구독자에게 상태 변화가 전달되고, 해제하면 더 오지 않는다', async () => {
    const provider = fakeProvider({ eth_requestAccounts: [LOWER], eth_chainId: '0x1' })
    const client = createWalletClient(provider)
    const seen: string[] = []
    const unsubscribe = client.subscribe((s) => seen.push(s.status))

    await client.connect()
    expect(seen).toContain('connecting')
    expect(seen).toContain('connected')

    unsubscribe()
    const before = seen.length
    provider.emit('accountsChanged', [])
    expect(seen).toHaveLength(before)
  })
})

describe('리스너 생명주기', () => {
  it('생성만으로는 provider 에 아무것도 등록하지 않는다', () => {
    const provider = fakeProvider()
    createWalletClient(provider)

    expect(provider.handlerCount('accountsChanged')).toBe(0)
    expect(provider.handlerCount('chainChanged')).toBe(0)
  })

  it('첫 구독에서 붙고 마지막 해제에서 떨어진다 (누수 방지)', () => {
    const provider = fakeProvider()
    const client = createWalletClient(provider)

    const off1 = client.subscribe(() => {})
    const off2 = client.subscribe(() => {})
    expect(provider.handlerCount('accountsChanged')).toBe(1)
    expect(provider.handlerCount('chainChanged')).toBe(1)

    off1()
    // 아직 구독자가 남아 있으므로 떼면 안 된다
    expect(provider.handlerCount('accountsChanged')).toBe(1)

    off2()
    expect(provider.handlerCount('accountsChanged')).toBe(0)
    expect(provider.handlerCount('chainChanged')).toBe(0)
  })

  // 이 테스트가 없어서 실기기 검증에서야 버그를 잡았다.
  // React StrictMode 는 개발 모드에서 마운트→언마운트→재마운트를 흉내내므로
  // 구독이 한 번 끊겼다가 다시 붙는다. 예전 구현은 생성 시점에 리스너를 등록하고
  // destroy() 로 뗐기 때문에, 재마운트 후 지갑 이벤트가 영영 오지 않았다.
  it('구독을 끊었다 다시 이어도 지갑 이벤트가 계속 전달된다 (StrictMode 재마운트)', () => {
    const provider = fakeProvider()
    const client = createWalletClient(provider)

    const off = client.subscribe(() => {})
    off()

    const seen: (string | null)[] = []
    client.subscribe((s) => seen.push(s.address))

    provider.emit('accountsChanged', [OTHER_CHECKSUM])

    expect(seen).toEqual([OTHER_CHECKSUM])
    expect(client.getState().address).toBe(OTHER_CHECKSUM)
  })

  it('구독자가 없는 동안 발생한 이벤트는 상태를 건드리지 않는다', () => {
    const provider = fakeProvider()
    const client = createWalletClient(provider)

    // 리스너가 붙어 있지 않으므로 emit 해도 아무 일이 없어야 한다
    provider.emit('chainChanged', '0x1')

    expect(client.getState().chainId).toBeNull()
  })
})

describe('disconnect', () => {
  it('로컬 상태만 비운다 (지갑에 요청을 보내지 않는다 — 그런 표준 API 가 없다)', async () => {
    const provider = fakeProvider({ eth_requestAccounts: [LOWER], eth_chainId: '0x1' })
    const client = createWalletClient(provider)
    await client.connect()
    const callsBefore = provider.calls.length

    client.disconnect()

    expect(client.getState()).toMatchObject({ status: 'disconnected', address: null })
    expect(provider.calls).toHaveLength(callsBefore)
  })
})

describe('switchChain', () => {
  it('지갑에 이미 있는 체인이면 그대로 전환한다', async () => {
    const provider = fakeProvider({ wallet_switchEthereumChain: null })
    const client = createWalletClient(provider)

    await expect(client.switchChain('0x539', 'http://rpc')).resolves.toBe('switched')
  })

  it('등록되지 않은 체인(4902)이면 addEthereumChain 으로 폴백한다', async () => {
    const provider = fakeProvider({
      wallet_switchEthereumChain: rpcError(4902),
      wallet_addEthereumChain: null,
    })
    const client = createWalletClient(provider)

    await expect(client.switchChain('0x539', 'http://rpc')).resolves.toBe('added')
    expect(provider.calls).toContain('wallet_addEthereumChain')
  })

  it('등록이 필요한데 RPC URL 이 없으면 시도조차 하지 않는다', async () => {
    // VITE_BESU_RPC_URL 이 아직 빈 값인 현재 상태가 정확히 이 경우다.
    const provider = fakeProvider({ wallet_switchEthereumChain: rpcError(4902) })
    const client = createWalletClient(provider)

    await expect(client.switchChain('0x539', null)).resolves.toBe('missing-rpc-url')
    expect(provider.calls).not.toContain('wallet_addEthereumChain')
  })

  it('사용자가 전환을 거부하면 rejected', async () => {
    const provider = fakeProvider({ wallet_switchEthereumChain: rpcError(4001) })
    const client = createWalletClient(provider)

    await expect(client.switchChain('0x539', 'http://rpc')).resolves.toBe('rejected')
  })

  it('체인 추가를 거부해도 rejected', async () => {
    const provider = fakeProvider({
      wallet_switchEthereumChain: rpcError(4902),
      wallet_addEthereumChain: rpcError(4001),
    })
    const client = createWalletClient(provider)

    await expect(client.switchChain('0x539', 'http://rpc')).resolves.toBe('rejected')
  })
})

describe('shortenAddress', () => {
  it('앞 6자와 뒤 4자만 남긴다', () => {
    expect(shortenAddress(CHECKSUM)).toBe('0xd8dA…6045')
  })

  it('짧은 문자열은 그대로 둔다', () => {
    expect(shortenAddress('0x1234')).toBe('0x1234')
  })
})
