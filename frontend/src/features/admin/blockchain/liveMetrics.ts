// 실시간 모니터가 "지금 흐르고 있다" 를 보여주기 위해 필요한 계산만 모은다.
//
// 전부 순수 함수다 — 시각은 인자로 받고 내부에서 Date.now() 를 부르지 않는다.
// 컴포넌트가 1초 tick 을 하나만 돌리고 그 값을 내려주면 되고, 테스트도 시간을 고정할 수 있다.

import type { LiveBlock } from './blockchainLiveApi.ts'

/** 평균 간격 표본 상한. */
const MAX_SAMPLES = 8

/**
 * 이보다 긴 간격은 표본에서 뺀다.
 *
 * 모니터링을 일시정지했다 재개하면 그 공백이 그대로 "간격" 으로 잡힌다. 그 값을 평균에
 * 섞으면 다음 블록 예상이 몇 분짜리가 되어 진행 바가 멈춘 것처럼 보인다.
 */
const MAX_PLAUSIBLE_GAP_MS = 60_000

/** 평균의 이 배수를 넘기면 지연으로 본다. 네트워크가 느려진 것을 정상인 척하지 않는다. */
const OVERDUE_FACTOR = 2

/** 레일에 한 번에 세우는 칸 수(블록 카드 + 접힌 칸). */
const TRAIN_LIMIT = 7

export interface BlockCadence {
  /** 최근 블록들의 평균 간격(ms). 표본이 없으면 null — 이때는 예상을 표시하지 않는다. */
  readonly averageMs: number | null
  readonly samples: number
}

export interface NextBlockEta {
  readonly elapsedMs: number
  /** 0~1 로 자른 진행률. 표시 전용이며 지연 여부는 overdue 로 따로 본다. */
  readonly ratio: number
  readonly overdue: boolean
}

export interface TrainBlockCar {
  readonly kind: 'BLOCK'
  readonly key: string
  readonly block: LiveBlock
  /** 화면에 있는 블록 중 최대 tx 수 대비 비율. 막대 길이는 이 값에서만 나온다. */
  readonly loadRatio: number
  /** 전체 tx 중 우리 컨트랙트 호출 비율. */
  readonly oursRatio: number
  readonly latest: boolean
}

export interface TrainGapCar {
  readonly kind: 'GAP'
  readonly key: string
  readonly count: number
  readonly fromNumber: number
  readonly toNumber: number
}

export type TrainCar = TrainBlockCar | TrainGapCar

/** 수신한 블록들의 타임스탬프로 실제 블록 간격을 잰다. blocks 는 최신이 앞이다. */
export function measureCadence(blocks: readonly LiveBlock[]): BlockCadence {
  const times = blocks
    .slice(0, MAX_SAMPLES)
    .map((block) => Date.parse(block.timestamp))
    .filter((value) => Number.isFinite(value))

  const gaps: number[] = []
  for (let index = 0; index + 1 < times.length; index += 1) {
    const gap = times[index] - times[index + 1]
    if (gap > 0 && gap <= MAX_PLAUSIBLE_GAP_MS) gaps.push(gap)
  }

  if (gaps.length === 0) return { averageMs: null, samples: 0 }
  const total = gaps.reduce((sum, gap) => sum + gap, 0)
  return { averageMs: Math.round(total / gaps.length), samples: gaps.length }
}

/** 마지막 블록 이후 얼마나 지났는지, 평균 대비 어디쯤인지. 표본이 없으면 null. */
export function measureNextBlock(
  latestTimestamp: string | null,
  averageMs: number | null,
  nowMs: number,
): NextBlockEta | null {
  if (!latestTimestamp || averageMs === null || averageMs <= 0) return null

  const confirmedAt = Date.parse(latestTimestamp)
  if (!Number.isFinite(confirmedAt)) return null

  // 노드 시각이 브라우저보다 살짝 앞설 수 있다. 음수 경과를 그대로 두면 진행 바가 거꾸로 간다.
  const elapsedMs = Math.max(0, nowMs - confirmedAt)
  return {
    elapsedMs,
    ratio: Math.min(1, elapsedMs / averageMs),
    overdue: elapsedMs > averageMs * OVERDUE_FACTOR,
  }
}

/**
 * 레일에 세울 칸을 만든다. blocks 는 최신이 앞이고, 결과는 왼쪽(과거)부터다.
 *
 * <b>비어 있는 블록을 걸러 내지 않고 접는 이유</b> — 번호를 건너뛴 채 고리로 이어 놓으면
 * "parentHash 로 묶여 있다" 는 그림 자체가 거짓이 된다. 접힌 칸이 사슬 위에 남아 있어야
 * 그 사이에도 블록이 존재한다는 사실이 유지되고, 실시간으로 숫자가 오르는 것도 보인다.
 *
 * `onlyOurs` 는 숨기는 스위치가 아니라 <b>접는 기준</b>을 바꾸는 스위치다. 꺼져 있으면
 * 트랜잭션이 하나도 없는 블록을, 켜져 있으면 우리 컨트랙트 호출이 없는 블록을 접는다.
 */
export function buildTrain(
  blocks: readonly LiveBlock[],
  onlyOurs: boolean,
  limit: number = TRAIN_LIMIT,
): TrainCar[] {
  const ordered = [...blocks].reverse()
  const peak = ordered.reduce((max, block) => Math.max(max, block.transactions.length), 0)
  const notable = (block: LiveBlock) =>
    onlyOurs ? block.projectTransactionCount > 0 : block.transactions.length > 0

  let latestKey: string | null = null
  ordered.forEach((block) => {
    if (notable(block)) latestKey = block.hash
  })

  const cars: TrainCar[] = []
  let gap: { count: number; from: number; to: number } | null = null

  const flushGap = () => {
    if (!gap) return
    cars.push({
      kind: 'GAP',
      key: `gap-${gap.from}-${gap.to}`,
      count: gap.count,
      fromNumber: gap.from,
      toNumber: gap.to,
    })
    gap = null
  }

  ordered.forEach((block) => {
    if (!notable(block)) {
      if (gap) {
        gap.count += 1
        gap.to = block.number
      } else {
        gap = { count: 1, from: block.number, to: block.number }
      }
      return
    }

    flushGap()
    const total = block.transactions.length
    const ours = Math.min(block.projectTransactionCount, total)
    cars.push({
      kind: 'BLOCK',
      key: block.hash,
      block,
      loadRatio: peak === 0 ? 0 : total / peak,
      oursRatio: total === 0 ? 0 : ours / total,
      latest: block.hash === latestKey,
    })
  })
  flushGap()

  return cars.slice(-limit)
}

/** 우리 컨트랙트를 호출한 트랜잭션 수의 합. 필터를 켰을 때 무엇이 남았는지 말해 준다. */
export function countOurTransactions(blocks: readonly LiveBlock[]): number {
  return blocks.reduce((sum, block) => sum + block.projectTransactionCount, 0)
}

/** "방금 / 3초 전 / 2분 11초 전". 절대 시각보다 흐름이 보인다. */
export function formatElapsed(isoTime: string | null, nowMs: number): string {
  if (!isoTime) return '기록 없음'
  const at = Date.parse(isoTime)
  if (!Number.isFinite(at)) return '기록 없음'

  const seconds = Math.max(0, Math.round((nowMs - at) / 1000))
  if (seconds < 1) return '방금'
  if (seconds < 60) return `${seconds}초 전`

  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}분 ${seconds % 60}초 전`
  return `${Math.floor(minutes / 60)}시간 ${minutes % 60}분 전`
}

/** 평균 간격 표기. 표본이 모이기 전에는 숫자를 지어내지 않는다. */
export function formatInterval(averageMs: number | null): string {
  if (averageMs === null) return '측정 중'
  if (averageMs < 1_000) return `${averageMs}ms`
  return `${(averageMs / 1_000).toFixed(1)}초`
}
