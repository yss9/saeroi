import { afterEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../../../stores/authStore.ts'
import { consumeBlockchainStream, type LiveBlock } from './blockchainLiveApi.ts'

describe('blockchain SSE client', () => {
  afterEach(() => {
    useAuthStore.getState().signOut()
    vi.restoreAllMocks()
  })

  it('parses split SSE chunks and preserves the block event id', async () => {
    useAuthStore.getState().signIn({
      id: 1,
      email: 'admin@saeroi.test',
      name: '관리자',
      role: 'ADMIN',
      walletAddress: null,
    }, 'access-token')

    const payload: LiveBlock = {
      chainId: 31221,
      number: 42,
      hash: `0x${'a'.repeat(64)}`,
      parentHash: `0x${'b'.repeat(64)}`,
      timestamp: '2026-09-21T00:00:00Z',
      transactions: [],
      projectTransactionCount: 0,
    }
    const text = `id:42\nevent:block\ndata:${JSON.stringify(payload)}\n\n`
    const encoded = new TextEncoder().encode(text)
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoded.slice(0, 19))
        controller.enqueue(encoded.slice(19))
        controller.close()
      },
    })
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(stream, {
      status: 200,
      headers: { 'Content-Type': 'text/event-stream' },
    }))
    const onBlock = vi.fn()

    await consumeBlockchainStream({
      lastEventId: '41',
      signal: new AbortController().signal,
      onOpen: vi.fn(),
      onBlock,
      onHeartbeat: vi.fn(),
    })

    expect(onBlock).toHaveBeenCalledWith(payload, '42')
    expect(fetch).toHaveBeenCalledWith('/api/blockchain/live/stream', expect.objectContaining({
      headers: expect.objectContaining({
        Authorization: 'Bearer access-token',
        'Last-Event-ID': '41',
      }),
    }))
  })
})
