import { env } from '../../../config/env.ts'
import { apiClient } from '../../../services/apiClient.ts'
import { getAccessToken } from '../../../stores/authStore.ts'
import type { ChainEvidence } from './blockchainVerification.ts'

export interface LiveTransaction {
  readonly hash: string
  readonly from: string
  readonly to: string | null
  readonly value: string
  readonly selector: string | null
  readonly contractName: string | null
  readonly functionName: string | null
}

export interface LiveBlock {
  readonly chainId: number
  readonly number: number
  readonly hash: string
  readonly parentHash: string
  readonly timestamp: string
  readonly transactions: readonly LiveTransaction[]
  readonly projectTransactionCount: number
}

interface HeartbeatEvent {
  readonly latestBlock: number
  readonly checkedAt: string
}

interface StreamHandlers {
  readonly lastEventId: string | null
  readonly signal: AbortSignal
  readonly onOpen: () => void
  readonly onBlock: (block: LiveBlock, eventId: string | null) => void
  readonly onHeartbeat: (heartbeat: HeartbeatEvent) => void
}

export async function getChainEvidence(txHash: string): Promise<ChainEvidence> {
  const { data } = await apiClient.get<ChainEvidence>(`/blockchain/live/transactions/${txHash}`)
  return data
}

/** Authorization 헤더가 필요한 관리자 SSE라 native EventSource 대신 fetch stream을 쓴다. */
export async function consumeBlockchainStream(handlers: StreamHandlers): Promise<void> {
  const token = getAccessToken()
  if (!token) throw new Error('관리자 로그인이 필요합니다.')

  const headers: Record<string, string> = {
    Accept: 'text/event-stream',
    Authorization: `Bearer ${token}`,
  }
  if (handlers.lastEventId) headers['Last-Event-ID'] = handlers.lastEventId

  const response = await fetch(apiUrl('/blockchain/live/stream'), {
    method: 'GET',
    headers,
    credentials: 'include',
    signal: handlers.signal,
  })
  if (!response.ok) {
    throw new Error(response.status === 401 || response.status === 403
      ? '관리자 인증이 만료되었거나 권한이 없습니다.'
      : `실시간 스트림 연결에 실패했습니다. (HTTP ${response.status})`)
  }
  if (!response.body) throw new Error('브라우저가 SSE 스트림을 열지 못했습니다.')
  handlers.onOpen()

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (!handlers.signal.aborted) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true }).replaceAll('\r\n', '\n')
    let boundary = buffer.indexOf('\n\n')
    while (boundary >= 0) {
      dispatchEvent(buffer.slice(0, boundary), handlers)
      buffer = buffer.slice(boundary + 2)
      boundary = buffer.indexOf('\n\n')
    }
  }
}

function dispatchEvent(raw: string, handlers: StreamHandlers) {
  let event = 'message'
  let id: string | null = null
  const data: string[] = []
  for (const line of raw.split('\n')) {
    if (line.startsWith(':')) continue
    const separator = line.indexOf(':')
    const field = separator < 0 ? line : line.slice(0, separator)
    const value = separator < 0 ? '' : line.slice(separator + 1).replace(/^ /, '')
    if (field === 'event') event = value
    if (field === 'id') id = value
    if (field === 'data') data.push(value)
  }
  if (data.length === 0) return
  const payload: unknown = JSON.parse(data.join('\n'))
  if (event === 'block' && isLiveBlock(payload)) handlers.onBlock(payload, id)
  if (event === 'heartbeat' && isHeartbeat(payload)) handlers.onHeartbeat(payload)
}

function apiUrl(path: string): string {
  const base = env.apiBaseUrl.endsWith('/') ? env.apiBaseUrl.slice(0, -1) : env.apiBaseUrl
  return `${base}${path}`
}

function isLiveBlock(value: unknown): value is LiveBlock {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<LiveBlock>
  return typeof candidate.number === 'number'
    && typeof candidate.hash === 'string'
    && Array.isArray(candidate.transactions)
}

function isHeartbeat(value: unknown): value is HeartbeatEvent {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<HeartbeatEvent>
  return typeof candidate.latestBlock === 'number' && typeof candidate.checkedAt === 'string'
}
