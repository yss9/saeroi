import { describe, expect, it } from 'vitest'
import {
  buildTrain,
  countOurTransactions,
  countStackColumns,
  formatElapsed,
  formatInterval,
  layoutStack,
  measureCadence,
  measureNextBlock,
  type TrainCar,
} from './liveMetrics.ts'
import type { LiveBlock } from './blockchainLiveApi.ts'

function block(number: number, isoTime: string, total = 0, ours = 0): LiveBlock {
  return {
    chainId: 31221,
    number,
    hash: `0x${number.toString(16).padStart(64, '0')}`,
    parentHash: `0x${(number - 1).toString(16).padStart(64, '0')}`,
    timestamp: isoTime,
    transactions: Array.from({ length: total }, (_, index) => ({
      hash: `0x${index.toString(16).padStart(64, 'a')}`,
      from: `0x${'c'.repeat(40)}`,
      to: `0x${'d'.repeat(40)}`,
      value: '0',
      selector: null,
      contractName: null,
      functionName: null,
    })),
    projectTransactionCount: ours,
  }
}

/** 최신이 앞인 실제 스트림 순서로 만든다. */
function stream(...blocks: LiveBlock[]): LiveBlock[] {
  return [...blocks].reverse()
}

describe('block cadence', () => {
  it('averages the gaps between consecutive blocks', () => {
    const blocks = [
      block(12, '2026-09-21T00:00:06Z'),
      block(11, '2026-09-21T00:00:04Z'),
      block(10, '2026-09-21T00:00:00Z'),
    ]
    expect(measureCadence(blocks)).toEqual({ averageMs: 3_000, samples: 2 })
  })

  it('ignores the long gap left by a paused monitor', () => {
    const blocks = [
      block(12, '2026-09-21T00:10:02Z'),
      block(11, '2026-09-21T00:10:00Z'),
      block(10, '2026-09-21T00:00:00Z'),
    ]
    expect(measureCadence(blocks)).toEqual({ averageMs: 2_000, samples: 1 })
  })

  it('reports no average until a second block arrives', () => {
    expect(measureCadence([block(10, '2026-09-21T00:00:00Z')])).toEqual({
      averageMs: null,
      samples: 0,
    })
  })
})

describe('next block estimate', () => {
  const confirmedAt = '2026-09-21T00:00:00Z'
  const now = Date.parse(confirmedAt)

  it('fills proportionally to the average interval', () => {
    expect(measureNextBlock(confirmedAt, 2_000, now + 1_000)?.ratio).toBe(0.5)
  })

  it('caps the bar at full instead of overflowing', () => {
    expect(measureNextBlock(confirmedAt, 2_000, now + 9_000)?.ratio).toBe(1)
  })

  it('marks a block that is late by more than twice the average', () => {
    expect(measureNextBlock(confirmedAt, 2_000, now + 5_000)?.overdue).toBe(true)
    expect(measureNextBlock(confirmedAt, 2_000, now + 3_000)?.overdue).toBe(false)
  })

  it('never runs backwards when the node clock leads the browser', () => {
    expect(measureNextBlock(confirmedAt, 2_000, now - 4_000)?.elapsedMs).toBe(0)
  })

  it('stays silent while no average exists', () => {
    expect(measureNextBlock(confirmedAt, null, now)).toBeNull()
  })
})

describe('train assembly', () => {
  const at = (second: number) => `2026-09-21T00:00:${String(second).padStart(2, '0')}Z`

  it('collapses a run of empty blocks into one car that keeps their range', () => {
    const cars = buildTrain(stream(
      block(10, at(0), 2, 1),
      block(11, at(2)),
      block(12, at(4)),
      block(13, at(6), 3, 0),
    ), false)

    expect(cars.map((car) => car.kind)).toEqual(['BLOCK', 'GAP', 'BLOCK'])
    const gap = cars[1]
    expect(gap.kind === 'GAP' && gap.count).toBe(2)
    expect(gap.kind === 'GAP' && gap.fromNumber).toBe(11)
    expect(gap.kind === 'GAP' && gap.toNumber).toBe(12)
  })

  it('returns cars oldest first so the rail reads left to right', () => {
    const cars = buildTrain(stream(block(10, at(0), 1, 1), block(11, at(2), 1, 1)), false)
    expect(cars.map((car) => (car.kind === 'BLOCK' ? car.block.number : 0))).toEqual([10, 11])
  })

  it('switches what counts as notable when only our transactions matter', () => {
    const blocks = stream(block(10, at(0), 4, 0), block(11, at(2), 2, 1))

    expect(buildTrain(blocks, false).map((car) => car.kind)).toEqual(['BLOCK', 'BLOCK'])
    expect(buildTrain(blocks, true).map((car) => car.kind)).toEqual(['GAP', 'BLOCK'])
  })

  it('marks only the newest notable block as latest', () => {
    const cars = buildTrain(stream(
      block(10, at(0), 1, 1),
      block(11, at(2), 1, 1),
      block(12, at(4)),
    ), false)
    const latest = cars.filter((car) => car.kind === 'BLOCK' && car.latest)
    expect(latest).toHaveLength(1)
    expect(latest[0].kind === 'BLOCK' && latest[0].block.number).toBe(11)
  })

  it('scales the load bar against the busiest block and never divides by zero', () => {
    const cars = buildTrain(stream(block(10, at(0), 4, 1), block(11, at(2), 2, 0)), false)
    expect(cars.map((car) => (car.kind === 'BLOCK' ? car.loadRatio : -1))).toEqual([1, 0.5])
    expect(cars[0].kind === 'BLOCK' && cars[0].oursRatio).toBe(0.25)
    // 하나뿐인 빈 블록도 걸러지지 않고 접힌 칸으로 남는다 — 그래야 번호가 이어진다.
    const empty = buildTrain(stream(block(10, at(0))), false)
    expect(empty.map((car) => car.kind)).toEqual(['GAP'])
  })

  it('keeps the newest cars when the rail overflows', () => {
    const many = stream(...Array.from({ length: 9 }, (_, index) => block(10 + index, at(index), 1, 1)))
    const cars = buildTrain(many, false, 3)
    expect(cars.map((car) => (car.kind === 'BLOCK' ? car.block.number : 0))).toEqual([16, 17, 18])
  })

  it('counts our transactions across the window', () => {
    expect(countOurTransactions(stream(block(10, at(0), 3, 2), block(11, at(2), 1, 1)))).toBe(3)
  })
})

describe('stack layout', () => {
  function cars(count: number): TrainCar[] {
    return Array.from({ length: count }, (_, index) => ({
      kind: 'BLOCK' as const,
      key: `car-${index}`,
      block: block(100 - index, '2026-09-21T00:00:00Z', 1, 0),
      loadRatio: 1,
      oursRatio: 0,
      latest: index === 0,
    }))
  }

  it('fills columns downward, then upward, so the chain stays one line', () => {
    const cells = layoutStack(cars(9), 3)

    expect(cells.map(({ column, row }) => `${column}:${row}`)).toEqual([
      '1:1', '1:2', '1:3',
      '2:3', '2:2', '2:1',
      '3:1', '3:2', '3:3',
    ])
  })

  it('crosses columns only where two columns meet at the same height', () => {
    const cells = layoutStack(cars(7), 3)

    // 칸을 건너는 고리는 각 칸의 마지막 블록에만 붙는다 — 마지막 칸은 뒤가 없어 붙지 않는다.
    expect(cells.filter(({ tieAcross }) => tieAcross).map(({ column, row }) => `${column}:${row}`))
      .toEqual(['1:3', '2:1'])
    // 건너는 두 자리는 높이가 같아야 고리를 곧게 눕힐 수 있다.
    expect(cells[2].row).toBe(cells[3].row)
    expect(cells[5].row).toBe(cells[6].row)
  })

  it('ties a block to the one below it whichever way the column fills', () => {
    const cells = layoutStack(cars(5), 3)

    // 내려가는 1열: 위 두 칸에서 고리가 내려간다.
    expect(cells.slice(0, 3).map(({ tieDown }) => tieDown)).toEqual([true, true, false])
    // 올라가는 2열은 3행·2행만 차 있으므로 3행에는 아래가 없고 2행에서만 내려간다.
    expect(cells.slice(3).map(({ row, tieDown }) => `${row}:${tieDown}`)).toEqual([
      '3:false',
      '2:true',
    ])
  })

  it('counts the columns the grid has to open', () => {
    expect(countStackColumns(cars(0), 3)).toBe(1)
    expect(countStackColumns(cars(1), 3)).toBe(1)
    expect(countStackColumns(cars(3), 3)).toBe(1)
    expect(countStackColumns(cars(4), 3)).toBe(2)
    expect(countStackColumns(cars(9), 3)).toBe(3)
  })
})

describe('elapsed formatting', () => {
  const at = '2026-09-21T00:00:00Z'
  const now = Date.parse(at)

  it('reads as a flow of time', () => {
    expect(formatElapsed(at, now)).toBe('방금')
    expect(formatElapsed(at, now + 3_000)).toBe('3초 전')
    expect(formatElapsed(at, now + 131_000)).toBe('2분 11초 전')
  })

  it('does not invent an interval before samples exist', () => {
    expect(formatInterval(null)).toBe('측정 중')
    expect(formatInterval(1_900)).toBe('1.9초')
  })
})
