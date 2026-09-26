import { isHexString } from 'ethers'
import { getChainEvidence } from './blockchainLiveApi.ts'

export interface ChainEvidence {
  readonly hash: string
  readonly chainId: string
  readonly blockNumber: number
  readonly blockHash: string
  readonly latestBlock: number
  readonly confirmations: number
  readonly timestamp: number
  readonly from: string
  readonly to: string | null
  readonly status: number
  readonly gasUsed: string
  readonly value: string
  readonly input: string
  readonly logCount: number
  readonly queriedAt: string
  readonly latencyMs: number
}

export type EvidenceSnapshot = Pick<
  ChainEvidence,
  'hash' | 'blockNumber' | 'blockHash' | 'from' | 'to' | 'status'
>

export interface EvidenceMismatch {
  readonly field: keyof EvidenceSnapshot
  readonly label: string
  readonly stored: string
  readonly onChain: string
}

const SNAPSHOT_LABELS: Readonly<Record<keyof EvidenceSnapshot, string>> = {
  hash: '트랜잭션 해시',
  blockNumber: '블록 번호',
  blockHash: '블록 해시',
  from: '보낸 주소',
  to: '받는 컨트랙트',
  status: '실행 상태',
}

/** 저장된 화면 값을 재사용하지 않고 백엔드가 Besu RPC에서 다시 읽은 원본을 가져온다. */
export async function queryChainEvidence(rawHash: string): Promise<ChainEvidence> {
  const hash = rawHash.trim()
  if (!isHexString(hash, 32)) {
    throw new Error('트랜잭션 해시는 0x로 시작하는 64자리 16진수여야 합니다.')
  }

  return getChainEvidence(hash)
}

export function toEvidenceSnapshot(evidence: ChainEvidence): EvidenceSnapshot {
  return {
    hash: evidence.hash,
    blockNumber: evidence.blockNumber,
    blockHash: evidence.blockHash,
    from: evidence.from,
    to: evidence.to,
    status: evidence.status,
  }
}

/** 체인은 건드리지 않고, 브라우저가 들고 있는 DB 응답 복제본만 오염시킨다. */
export function simulateSnapshotAttack(snapshot: EvidenceSnapshot): EvidenceSnapshot {
  return {
    ...snapshot,
    blockNumber: snapshot.blockNumber + 37,
    blockHash: replaceLastHex(snapshot.blockHash),
    to: snapshot.to ? replaceLastHex(snapshot.to) : '0x0000000000000000000000000000000000000001',
    status: snapshot.status === 1 ? 0 : 1,
  }
}

export function compareEvidence(
  stored: EvidenceSnapshot,
  evidence: ChainEvidence,
): EvidenceMismatch[] {
  const onChain = toEvidenceSnapshot(evidence)
  const fields = Object.keys(SNAPSHOT_LABELS) as (keyof EvidenceSnapshot)[]

  return fields.flatMap((field) => {
    if (normalize(stored[field]) === normalize(onChain[field])) return []
    return [{
      field,
      label: SNAPSHOT_LABELS[field],
      stored: display(stored[field]),
      onChain: display(onChain[field]),
    }]
  })
}

function replaceLastHex(value: string): string {
  const last = value.at(-1)?.toLowerCase()
  return `${value.slice(0, -1)}${last === 'f' ? 'e' : 'f'}`
}

function normalize(value: string | number | null): string {
  return String(value ?? '').toLowerCase()
}

function display(value: string | number | null): string {
  return value === null ? '없음' : String(value)
}
