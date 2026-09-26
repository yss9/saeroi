import { describe, expect, it } from 'vitest'
import {
  compareEvidence,
  simulateSnapshotAttack,
  toEvidenceSnapshot,
  type ChainEvidence,
} from './blockchainVerification.ts'

const evidence: ChainEvidence = {
  hash: `0x${'a'.repeat(64)}`,
  chainId: '0x539',
  blockNumber: 1200,
  blockHash: `0x${'b'.repeat(64)}`,
  latestBlock: 1210,
  confirmations: 11,
  timestamp: 1_700_000_000,
  from: `0x${'c'.repeat(40)}`,
  to: `0x${'d'.repeat(40)}`,
  status: 1,
  gasUsed: '42000',
  value: '0',
  input: '0x1234',
  logCount: 2,
  queriedAt: '2026-09-21T00:00:00.000Z',
  latencyMs: 83,
}

describe('blockchain evidence comparison', () => {
  it('accepts an unchanged snapshot', () => {
    expect(compareEvidence(toEvidenceSnapshot(evidence), evidence)).toEqual([])
  })

  it('detects every field changed by the safe attack simulation', () => {
    const attacked = simulateSnapshotAttack(toEvidenceSnapshot(evidence))
    expect(compareEvidence(attacked, evidence).map((item) => item.field)).toEqual([
      'blockNumber',
      'blockHash',
      'to',
      'status',
    ])
  })

  it('compares hexadecimal evidence without case sensitivity', () => {
    const upper = {
      ...toEvidenceSnapshot(evidence),
      hash: evidence.hash.toUpperCase().replace('0X', '0x'),
      from: evidence.from.toUpperCase().replace('0X', '0x'),
    }
    expect(compareEvidence(upper, evidence)).toEqual([])
  })
})
