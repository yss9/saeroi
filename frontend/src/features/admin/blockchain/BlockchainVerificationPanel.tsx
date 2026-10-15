import { ArrowClockwise, Pause, Play, Pulse, ShieldCheck, Warning } from '@phosphor-icons/react'
import { type FormEvent, useEffect, useMemo, useRef, useState } from 'react'
import { queryChainEvidence, type ChainEvidence } from './blockchainVerification.ts'
import {
  consumeBlockchainStream,
  type LiveBlock,
  type LiveTransaction,
} from './blockchainLiveApi.ts'
import {
  buildTrain,
  countStackColumns,
  formatElapsed,
  formatInterval,
  layoutStack,
  measureCadence,
  measureNextBlock,
} from './liveMetrics.ts'
import './blockchain-verification.css'

type QueryStatus = 'IDLE' | 'LOADING' | 'SUCCESS' | 'ERROR'

/** 화면에 유지하는 블록 수. 접히는 칸이 있으므로 타워에 쌓는 칸 수보다 넉넉해야 한다. */
const BLOCK_BUFFER = 24

/** 피드에 세우는 트랜잭션 수. */
const FEED_LIMIT = 8

export function BlockchainVerificationPanel() {
  const [hash, setHash] = useState('')
  const [status, setStatus] = useState<QueryStatus>('IDLE')
  const [evidence, setEvidence] = useState<ChainEvidence | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [monitoring, setMonitoring] = useState(true)
  const [liveBlocks, setLiveBlocks] = useState<LiveBlock[]>([])
  const [monitorError, setMonitorError] = useState<string | null>(null)
  const [lastCheckedAt, setLastCheckedAt] = useState<string | null>(null)
  const [streamConnected, setStreamConnected] = useState(false)
  /** 다음 재시도 시각. 끊겼을 때 "몇 초 뒤" 인지 보여주려고 들고 있다. */
  const [reconnectAt, setReconnectAt] = useState<number | null>(null)
  const [queryStartedAt, setQueryStartedAt] = useState<number | null>(null)
  /** 온체인 원본의 나머지 항목을 펼칠지. 기본은 접힘 — 먼저 읽어야 할 여섯 줄만 세운다. */
  const [allFields, setAllFields] = useState(false)
  const lastEventIdRef = useRef<string | null>(null)

  // 이벤트가 오지 않는 동안에도 시간은 흐른다. 이 tick 이 없으면 블록 사이 몇 초 동안
  // 화면이 완전히 정지해 "끊긴 것" 처럼 보인다.
  const now = useTicker(true, 1_000)
  // RPC 왕복은 보통 수백 ms 라 1초 tick 으로는 숫자가 한 번도 안 올라간다.
  const queryNow = useTicker(status === 'LOADING', 100)

  useEffect(() => {
    if (!monitoring) return
    const controller = new AbortController()

    async function connect() {
      let retryDelay = 1_000
      while (!controller.signal.aborted) {
        try {
          await consumeBlockchainStream({
            lastEventId: lastEventIdRef.current,
            signal: controller.signal,
            onOpen: () => {
              setStreamConnected(true)
              setMonitorError(null)
              setReconnectAt(null)
              retryDelay = 1_000
            },
            onBlock: (block, eventId) => {
              if (eventId) lastEventIdRef.current = eventId
              setLiveBlocks((current) => [
                block,
                ...current.filter((item) => item.hash !== block.hash),
              ].slice(0, BLOCK_BUFFER))
            },
            onHeartbeat: (heartbeat) => setLastCheckedAt(heartbeat.checkedAt),
          })
          if (!controller.signal.aborted) throw new Error('실시간 스트림 연결이 종료되었습니다.')
        } catch (reason) {
          if (controller.signal.aborted) break
          setStreamConnected(false)
          setMonitorError(reason instanceof Error ? reason.message : 'SSE 연결에 실패했습니다.')
          setReconnectAt(Date.now() + retryDelay)
          await wait(retryDelay, controller.signal)
          retryDelay = Math.min(retryDelay * 2, 10_000)
        }
      }
    }

    void connect()
    return () => {
      controller.abort()
      setStreamConnected(false)
      setReconnectAt(null)
    }
  }, [monitoring])

  async function inspect(event?: FormEvent, selectedHash?: string) {
    event?.preventDefault()
    if (status === 'LOADING') return

    const targetHash = selectedHash ?? hash
    if (selectedHash) changeHash(selectedHash)
    setStatus('LOADING')
    setQueryStartedAt(Date.now())
    setError(null)
    try {
      setEvidence(await queryChainEvidence(targetHash))
      setStatus('SUCCESS')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'RPC 조회 중 알 수 없는 오류가 발생했습니다.')
      setStatus('ERROR')
    }
  }

  function changeHash(value: string) {
    setHash(value)
    setEvidence(null)
    setError(null)
    setStatus('IDLE')
  }

  const querying = status === 'LOADING'

  const latest = liveBlocks[0] ?? null
  const cadence = useMemo(() => measureCadence(liveBlocks), [liveBlocks])
  const cars = useMemo(() => buildTrain(liveBlocks, true), [liveBlocks])
  // buildTrain 은 과거가 앞이다. 타워는 최신이 첫 자리이므로 뒤집어 넘긴다.
  const cells = useMemo(() => layoutStack([...cars].reverse()), [cars])
  const stackColumns = countStackColumns(cars)
  const feed = useMemo(() => recentTransactions(liveBlocks), [liveBlocks])
  const eta = measureNextBlock(latest?.timestamp ?? null, cadence.averageMs, now)
  const elapsedQueryMs = queryStartedAt === null ? 0 : Math.max(0, queryNow - queryStartedAt)

  return (
    <section className="chain-lab" aria-labelledby="chain-lab-title">
      <header className="chain-lab__hero">
        <h1 id="chain-lab-title">블록체인 검증</h1>
        <div className="chain-lab__hero-side">
          <StreamStatus
            monitoring={monitoring}
            connected={streamConnected}
            lastCheckedAt={lastCheckedAt}
            reconnectAt={reconnectAt}
            now={now}
          />
          <button
            type="button"
            className="chain-lab__monitor-toggle"
            onClick={() => setMonitoring((current) => !current)}
          >
            {monitoring ? <Pause aria-hidden="true" /> : <Play aria-hidden="true" />}
            {monitoring ? '일시정지' : '모니터링 시작'}
          </button>
        </div>
      </header>

      <section className="chain-lab__train" aria-labelledby="chain-train-title">
        <div className="chain-lab__train-head">
          <div className="chain-lab__train-title">
            <h2 id="chain-train-title">최근 새로이 거래 블록</h2>
            {latest ? (
              <p className="chain-lab__head-block">
                {/* key 가 바뀌면 요소가 다시 mount 되어 한 번짜리 강조가 재생된다. */}
                <strong key={latest.hash}>#{formatNumber(latest.number)}</strong>
                <small>{formatElapsed(latest.timestamp, now)}</small>
              </p>
            ) : null}
          </div>
        </div>

        {monitorError ? (
          <p className="chain-lab__monitor-error" role="status">
            <Warning aria-hidden="true" />
            {monitorError}
          </p>
        ) : null}

        <div className="chain-lab__yard">
          {cells.length > 0 ? (
            <>
              {/* 다음 블록이 앉을 빈 자리. 간격 표본이 없으면 그리지 않는다 — 재지도 않은 예상을 말하게 된다. */}
              {cadence.averageMs !== null ? (
                <p className="chain-lab__next">
                  다음
                  <code>{formatInterval(cadence.averageMs)}</code>
                </p>
              ) : null}
              <ol
                className="chain-lab__stack"
                aria-label="최근 블록"
                style={{ gridTemplateColumns: `repeat(${stackColumns}, var(--chain-box))` }}
              >
                {cells.map(({ car, column, row, tieDown, tieAcross }) => (
                  <li key={car.key} style={{ gridColumn: column, gridRow: row }}>
                    {car.kind === 'GAP' ? (
                      <div className="chain-lab__gap">
                        <i />
                        <div className="chain-lab__slab">
                          <p className="chain-lab__slab-face">
                            {car.count === 1
                              ? `#${formatNumber(car.fromNumber)}`
                              : `#${formatNumber(car.fromNumber)}–${formatNumber(car.toNumber)}`}
                          </p>
                        </div>
                        <i />
                      </div>
                    ) : (
                      <article className={`chain-lab__brick ${car.latest ? 'is-latest' : ''}`}>
                        <div className="chain-lab__brick-face">
                          <strong>#{formatNumber(car.block.number)}</strong>
                          {/* 막대 길이가 "전체 N건" 을 대신한다. 0건 블록도 최소 폭은 남겨 칸이 비어 보이지 않게 한다. */}
                          <div className="chain-lab__brick-meter" aria-hidden="true">
                            <i style={{ width: `${Math.max(6, car.loadRatio * 100)}%` }} />
                          </div>
                          {car.block.projectTransactionCount > 0 ? (
                            <span className="chain-lab__brick-ours">
                              saeroi {formatNumber(car.block.projectTransactionCount)}
                            </span>
                          ) : null}
                        </div>
                      </article>
                    )}
                    {tieDown ? <span className="chain-lab__tie is-down" aria-hidden="true" /> : null}
                    {tieAcross ? <span className="chain-lab__tie is-across" aria-hidden="true" /> : null}
                  </li>
                ))}
              </ol>
            </>
          ) : (
            <p className="chain-lab__stack-wait">
              <Pulse aria-hidden="true" />
              최신 블록을 기다리고 있습니다.
            </p>
          )}
        </div>

        <div className="chain-lab__cadence">
          {/* 진행은 CSS 애니메이션이 그린다. 매 프레임 렌더하지 않으면서도 부드럽고,
              모션을 줄인 환경에서는 통째로 숨기면 되도록 텍스트에 같은 정보를 남겼다.
              간격 표본이 없으면 그리지 않는다 — 임의의 기본값으로 채우면 재지도 않은
              속도를 보여주게 된다. */}
          {cadence.averageMs !== null ? (
            <div className={`chain-lab__progress ${eta?.overdue ? 'is-overdue' : ''}`} aria-hidden="true">
              <i key={latest?.hash ?? 'none'} style={{ animationDuration: `${cadence.averageMs}ms` }} />
            </div>
          ) : (
            <div className="chain-lab__progress is-empty" aria-hidden="true" />
          )}
          <small>
            {/* 접혀 있어도 간격은 받은 블록 전체로 잰 값이라 그대로 말한다.
                모션을 줄인 환경에서는 진행 바가 숨으므로 "경과" 는 남겨 둔다 — 이 문구가 유일한 단서가 된다. */}
            {cadence.samples > 0
              ? `${eta?.overdue ? '지연 · ' : ''}평균 ${formatInterval(cadence.averageMs)} · ${Math.round((eta?.elapsedMs ?? 0) / 1000)}초 경과`
              : '측정 중'}
          </small>
        </div>
      </section>

      <div className="chain-lab__side">
        <form className="chain-lab__search" onSubmit={inspect}>
          <label htmlFor="chain-transaction-hash">트랜잭션 해시</label>
          <div>
            <input
              id="chain-transaction-hash"
              value={hash}
              onChange={(event) => changeHash(event.target.value)}
              placeholder="0x로 시작하는 66자리 해시를 붙여 넣으세요"
              spellCheck={false}
              autoComplete="off"
            />
            <button type="submit" disabled={querying || hash.trim().length === 0}>
              <ArrowClockwise aria-hidden="true" />
              조회
            </button>
          </div>
        </form>

        {error ? (
          <div className="chain-lab__error" role="alert">
            <Warning aria-hidden="true" />
            {error}
          </div>
        ) : null}

        {evidence ? (
          <section className={`chain-lab__evidence ${querying ? 'is-stale' : ''}`} aria-labelledby="chain-evidence-title" aria-busy={querying}>
            <header>
              <strong id="chain-evidence-title">조회 결과</strong>
              <div className="chain-lab__evidence-badges">
                <span className={`chain-lab__status ${evidence.status === 1 ? 'is-good' : 'is-bad'}`}>
                  {evidence.status === 1 ? 'SUCCESS' : 'REVERTED'}
                </span>
                <span className="chain-lab__latency">
                  {querying ? '조회 중…' : `RPC ${formatNumber(evidence.latencyMs)} ms`}
                </span>
              </div>
            </header>

            <dl>
              <Row label="해시" value={evidence.hash} wide />
              <Row label="블록" value={`#${formatNumber(evidence.blockNumber)}`} />
              <Row label="확정" value={formatNumber(evidence.confirmations)} />
              <Row label="보낸 주소" value={evidence.from} />
              <Row label="받는 컨트랙트" value={evidence.to ?? '컨트랙트 생성'} />
              <Row label="가스" value={formatNumber(Number(evidence.gasUsed))} />
              {/* 나머지는 접어 둔다. 열두 줄을 한꺼번에 세우면 어느 줄도 먼저 읽히지 않는다. */}
              {allFields ? (
                <>
                  <Row label="블록 해시" value={evidence.blockHash} wide />
                  <Row label="기록 시각" value={formatDate(new Date(evidence.timestamp * 1000).toISOString())} />
                  <Row label="현재 블록" value={`#${formatNumber(evidence.latestBlock)}`} />
                  <Row label="이벤트 로그" value={formatNumber(evidence.logCount)} />
                  <Row label="전송 값(wei)" value={evidence.value} />
                  <Row
                    label="호출 데이터"
                    value={evidence.input === '0x' ? '없음' : `${evidence.input.slice(0, 18)}… (${formatNumber(evidence.input.length)}자)`}
                  />
                  <Row label="체인" value={evidence.chainId} />
                  <Row label="재조회" value={formatElapsed(evidence.queriedAt, now)} />
                </>
              ) : null}
            </dl>

            <button type="button" className="chain-lab__more" onClick={() => setAllFields((current) => !current)}>
              {allFields ? '접기' : '전체 항목'}
            </button>
          </section>
        ) : querying ? (
          <div className="chain-lab__empty" aria-busy="true">
            <Pulse aria-hidden="true" />
            <strong>조회 중 · {formatNumber(elapsedQueryMs)} ms</strong>
          </div>
        ) : (
          <div className="chain-lab__empty">
            <ShieldCheck aria-hidden="true" />
            <strong>해시를 입력하거나 아래에서 트랜잭션을 고르세요</strong>
          </div>
        )}

        <section className="chain-lab__feed" aria-label="실시간 새로이 거래">
          <div className="chain-lab__feed-head">새로이 거래</div>
          {feed.length > 0 ? feed.map(({ transaction, block }) => (
            <button type="button" key={transaction.hash} onClick={() => void inspect(undefined, transaction.hash)}>
              <span className="chain-lab__tx-kind is-ours">새로이</span>
              <span className="chain-lab__tx-main">
                <code>{shorten(transaction.hash)}</code>
                <small>
                  {`${transaction.contractName}${transaction.functionName ? ` · ${transaction.functionName}()` : ''}`}
                </small>
              </span>
              <span className="chain-lab__tx-block">
                #{formatNumber(block.number)}
                <small>{formatElapsed(block.timestamp, now)}</small>
              </span>
            </button>
          )) : (
            <p className="chain-lab__feed-empty">새로이 거래 없음</p>
          )}
        </section>
      </div>
    </section>
  )
}

/**
 * 스트림 상태를 한 덩어리로 말한다.
 *
 * 점 하나만 있을 때는 "조용한 것" 과 "끊긴 것" 이 같아 보였다. 끊겼다면 언제 다시 붙는지까지
 * 보여야 관리자가 기다릴지 새로고침할지 판단할 수 있다.
 */
function StreamStatus({
  monitoring,
  connected,
  lastCheckedAt,
  reconnectAt,
  now,
}: {
  monitoring: boolean
  connected: boolean
  lastCheckedAt: string | null
  reconnectAt: number | null
  now: number
}) {
  if (!monitoring) return <span className="chain-lab__stream is-paused">일시정지</span>

  if (!connected) {
    const seconds = reconnectAt === null ? null : Math.max(0, Math.ceil((reconnectAt - now) / 1000))
    return (
      <span className="chain-lab__stream is-retrying">
        재연결{seconds !== null ? ` · ${seconds}초` : ''}
      </span>
    )
  }

  return (
    <span className="chain-lab__stream is-live">
      <i aria-hidden="true" />
      LIVE
      <small>{formatElapsed(lastCheckedAt, now)}</small>
    </span>
  )
}

function Row({ label, value, wide = false }: { label: string; value: string; wide?: boolean }) {
  return (
    <div className={wide ? 'is-wide' : undefined}>
      <dt>{label}</dt>
      <dd title={value}>{value}</dd>
    </div>
  )
}

/**
 * 일정 주기로 현재 시각을 돌려준다.
 *
 * 탭이 가려지면 멈춘다 — 보이지도 않는 화면 때문에 초당 렌더를 돌릴 이유가 없고,
 * 브라우저가 백그라운드 타이머를 조이면 어차피 값이 튄다. 다시 보이는 순간 한 번
 * 당겨 맞춰서 "몇 분 전" 이 멈춰 있던 것처럼 보이지 않게 한다.
 */
function useTicker(active: boolean, intervalMs: number): number {
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    if (!active) return
    let timer = 0

    const stop = () => {
      window.clearInterval(timer)
      timer = 0
    }
    const start = () => {
      setNow(Date.now())
      timer = window.setInterval(() => setNow(Date.now()), intervalMs)
    }
    const handleVisibility = () => {
      stop()
      if (!document.hidden) start()
    }

    if (!document.hidden) start()
    document.addEventListener('visibilitychange', handleVisibility)
    return () => {
      stop()
      document.removeEventListener('visibilitychange', handleVisibility)
    }
  }, [active, intervalMs])

  return now
}

function formatNumber(value: number): string {
  return value.toLocaleString('ko-KR')
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'short', timeStyle: 'medium' }).format(new Date(value))
}

function wait(milliseconds: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    if (signal.aborted) return resolve()
    const finish = () => {
      window.clearTimeout(timer)
      signal.removeEventListener('abort', finish)
      resolve()
    }
    const timer = window.setTimeout(finish, milliseconds)
    signal.addEventListener('abort', finish, { once: true })
  })
}

function recentTransactions(blocks: readonly LiveBlock[]): { transaction: LiveTransaction; block: LiveBlock }[] {
  return blocks
    .flatMap((block) => block.transactions.map((transaction) => ({ transaction, block })))
    .filter((entry) => entry.transaction.contractName !== null)
    .slice(0, FEED_LIMIT)
}

function shorten(value: string): string {
  return value.length > 30 ? `${value.slice(0, 14)}…${value.slice(-10)}` : value
}
