import {
  ArrowClockwise,
  CheckCircle,
  Database,
  Flask,
  Pause,
  Play,
  Pulse,
  ShieldCheck,
  Warning,
} from '@phosphor-icons/react'
import { type FormEvent, type ReactNode, useEffect, useRef, useState } from 'react'
import {
  compareEvidence,
  queryChainEvidence,
  simulateSnapshotAttack,
  toEvidenceSnapshot,
  type ChainEvidence,
  type EvidenceMismatch,
  type EvidenceSnapshot,
} from './blockchainVerification.ts'
import {
  consumeBlockchainStream,
  type LiveBlock,
  type LiveTransaction,
} from './blockchainLiveApi.ts'
import './blockchain-verification.css'

type QueryStatus = 'IDLE' | 'LOADING' | 'SUCCESS' | 'ERROR'

export function BlockchainVerificationPanel() {
  const [hash, setHash] = useState('')
  const [status, setStatus] = useState<QueryStatus>('IDLE')
  const [evidence, setEvidence] = useState<ChainEvidence | null>(null)
  const [snapshot, setSnapshot] = useState<EvidenceSnapshot | null>(null)
  const [mismatches, setMismatches] = useState<EvidenceMismatch[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [attackActive, setAttackActive] = useState(false)
  const [monitoring, setMonitoring] = useState(true)
  const [liveBlocks, setLiveBlocks] = useState<LiveBlock[]>([])
  const [monitorError, setMonitorError] = useState<string | null>(null)
  const [lastCheckedAt, setLastCheckedAt] = useState<string | null>(null)
  const [streamConnected, setStreamConnected] = useState(false)
  const lastEventIdRef = useRef<string | null>(null)

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
              retryDelay = 1_000
            },
            onBlock: (block, eventId) => {
              if (eventId) lastEventIdRef.current = eventId
              setLiveBlocks((current) => [block, ...current.filter((item) => item.hash !== block.hash)].slice(0, 8))
            },
            onHeartbeat: (heartbeat) => setLastCheckedAt(heartbeat.checkedAt),
          })
          if (!controller.signal.aborted) throw new Error('실시간 스트림 연결이 종료되었습니다.')
        } catch (reason) {
          if (controller.signal.aborted) break
          setStreamConnected(false)
          setMonitorError(reason instanceof Error ? reason.message : 'SSE 연결에 실패했습니다.')
          await wait(retryDelay, controller.signal)
          retryDelay = Math.min(retryDelay * 2, 10_000)
        }
      }
    }

    void connect()
    return () => {
      controller.abort()
      setStreamConnected(false)
    }
  }, [monitoring])

  async function inspect(event?: FormEvent, selectedHash?: string) {
    event?.preventDefault()
    if (status === 'LOADING') return

    const targetHash = selectedHash ?? hash
    if (selectedHash) changeHash(selectedHash)
    setStatus('LOADING')
    setError(null)
    try {
      const fresh = await queryChainEvidence(targetHash)
      const currentSnapshot = selectedHash ? toEvidenceSnapshot(fresh) : (snapshot ?? toEvidenceSnapshot(fresh))
      setEvidence(fresh)
      setSnapshot(currentSnapshot)
      setMismatches(compareEvidence(currentSnapshot, fresh))
      setStatus('SUCCESS')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'RPC 조회 중 알 수 없는 오류가 발생했습니다.')
      setStatus('ERROR')
    }
  }

  function changeHash(value: string) {
    setHash(value)
    setEvidence(null)
    setSnapshot(null)
    setMismatches(null)
    setAttackActive(false)
    setError(null)
    setStatus('IDLE')
  }

  function runAttack() {
    if (!snapshot) return
    setSnapshot(simulateSnapshotAttack(snapshot))
    setMismatches(null)
    setAttackActive(true)
  }

  function restore() {
    if (!evidence) return
    const clean = toEvidenceSnapshot(evidence)
    setSnapshot(clean)
    setMismatches(compareEvidence(clean, evidence))
    setAttackActive(false)
  }

  const verified = mismatches !== null && mismatches.length === 0
  const compromised = mismatches !== null && mismatches.length > 0

  return (
    <section className="chain-lab" aria-labelledby="chain-lab-title">
      <header className="chain-lab__hero">
        <div>
          <p className="chain-lab__eyebrow">SSAFY BESU · LIVE RPC</p>
          <h1 id="chain-lab-title">블록체인 검증 대시보드</h1>
          <p>
            저장된 화면 값을 믿지 않고, 트랜잭션 해시를 기준으로 SSAFY 네트워크에서
            영수증과 블록을 매번 다시 읽어 원본 여부를 검증합니다.
          </p>
        </div>
        <div className={`chain-lab__live ${status === 'LOADING' ? 'is-querying' : ''}`}>
          <Pulse weight="fill" aria-hidden="true" />
          <span>{status === 'LOADING' ? 'RPC 조회 중' : '실시간 조회 대기'}</span>
        </div>
      </header>

      <section className="chain-lab__monitor" aria-labelledby="chain-monitor-title">
        <div className="chain-lab__monitor-head">
          <div>
            <span className={`chain-lab__monitor-dot ${monitoring && streamConnected ? 'is-live' : ''}`} aria-hidden="true" />
            <div><h2 id="chain-monitor-title">실시간 블록 모니터</h2><p>백엔드가 SSAFY Besu를 폴링하고 새 블록을 SSE로 즉시 전달합니다.</p></div>
          </div>
          <div className="chain-lab__monitor-actions">
            {lastCheckedAt ? <span>노드 확인 {formatTime(lastCheckedAt)}</span> : <span>{streamConnected ? 'SSE 연결됨' : '연결 중'}</span>}
            <button type="button" onClick={() => setMonitoring((current) => !current)}>
              {monitoring ? <Pause aria-hidden="true" /> : <Play aria-hidden="true" />}
              {monitoring ? '일시정지' : '모니터링 시작'}
            </button>
          </div>
        </div>

        {monitorError ? <div className="chain-lab__monitor-error"><Warning aria-hidden="true" />{monitorError}</div> : null}

        <div className="chain-lab__block-strip" aria-label="최근 감지 블록">
          {liveBlocks.length > 0 ? liveBlocks.map((block, index) => (
            <article className={index === 0 ? 'is-latest' : undefined} key={block.hash}>
              <div><span>BLOCK</span><strong>#{formatNumber(block.number)}</strong></div>
              <div className="chain-lab__block-bars" aria-hidden="true">
                {Array.from({ length: 7 }, (_, bar) => <i key={bar} style={{ height: `${18 + ((block.number + bar * 7) % 24)}%` }} />)}
              </div>
              <footer><span>{block.transactions.length} tx</span><span>{block.projectTransactionCount} project</span></footer>
            </article>
          )) : <div className="chain-lab__monitor-wait"><Pulse aria-hidden="true" />최신 블록을 기다리고 있습니다.</div>}
        </div>

        <div className="chain-lab__feed">
          <div className="chain-lab__feed-head"><span>실시간 트랜잭션</span><span>최근 블록에 포함된 순서</span></div>
          {recentTransactions(liveBlocks).length > 0 ? recentTransactions(liveBlocks).map(({ transaction, block }) => (
            <button type="button" key={transaction.hash} onClick={() => void inspect(undefined, transaction.hash)}>
              <span className={`chain-lab__tx-kind ${transaction.contractName ? 'is-project' : ''}`}>{transaction.contractName ? 'PROJECT' : 'NETWORK'}</span>
              <span className="chain-lab__tx-main"><code>{shorten(transaction.hash)}</code><small>{transaction.contractName ? `${transaction.contractName}${transaction.functionName ? ` · ${transaction.functionName}()` : ''}` : `${shorten(transaction.from)} → ${shorten(transaction.to ?? 'CREATE')}`}</small></span>
              <span className="chain-lab__tx-block">#{formatNumber(block.number)}<small>{formatUnixTime(block.timestamp)}</small></span>
              <span className="chain-lab__tx-open">검증하기 →</span>
            </button>
          )) : <p className="chain-lab__feed-empty">현재 표시 중인 블록에는 트랜잭션이 없습니다. 새 거래가 확정되면 여기에 즉시 나타납니다.</p>}
        </div>
      </section>

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
          <button type="submit" disabled={status === 'LOADING' || hash.trim().length === 0}>
            <ArrowClockwise aria-hidden="true" />
            {evidence ? '체인에서 다시 조회' : 'SSAFY 체인 조회'}
          </button>
        </div>
        <small>백엔드가 SSAFY Besu에서 직접 재조회합니다. MetaMask 연결, 서명 또는 가스가 필요하지 않습니다.</small>
      </form>

      {error ? <div className="chain-lab__error" role="alert"><Warning aria-hidden="true" />{error}</div> : null}

      {evidence ? (
        <>
          <section className="chain-lab__summary" aria-label="RPC 조회 요약">
            <Metric label="트랜잭션 상태" value={evidence.status === 1 ? 'SUCCESS' : 'REVERTED'} tone={evidence.status === 1 ? 'good' : 'danger'} />
            <Metric label="현재 블록" value={`#${formatNumber(evidence.latestBlock)}`} />
            <Metric label="확정 수" value={`${formatNumber(evidence.confirmations)} confirmations`} />
            <Metric label="RPC 응답" value={`${formatNumber(evidence.latencyMs)} ms`} />
          </section>

          <section className="chain-lab__evidence" aria-labelledby="chain-evidence-title">
            <div className="chain-lab__section-heading">
              <div><Pulse aria-hidden="true" /><span><strong id="chain-evidence-title">방금 읽은 온체인 원본</strong><small>{formatDate(evidence.queriedAt)} 재조회</small></span></div>
              <span className="chain-lab__network">chain {evidence.chainId}</span>
            </div>
            <dl>
              <EvidenceRow label="트랜잭션 해시" value={evidence.hash} wide />
              <EvidenceRow label="블록 번호" value={`#${formatNumber(evidence.blockNumber)}`} />
              <EvidenceRow label="기록 시각" value={formatDate(new Date(evidence.timestamp * 1000).toISOString())} />
              <EvidenceRow label="블록 해시" value={evidence.blockHash} wide />
              <EvidenceRow label="보낸 주소" value={evidence.from} />
              <EvidenceRow label="받는 컨트랙트" value={evidence.to ?? '컨트랙트 생성'} />
              <EvidenceRow label="가스 사용량" value={formatNumber(Number(evidence.gasUsed))} />
              <EvidenceRow label="이벤트 로그" value={`${formatNumber(evidence.logCount)}개`} />
              <EvidenceRow label="전송 값(wei)" value={evidence.value} />
              <EvidenceRow label="호출 데이터" value={evidence.input === '0x' ? '없음' : `${evidence.input.slice(0, 18)}… (${evidence.input.length}자)`} />
            </dl>
          </section>

          <section className="chain-lab__attack" aria-labelledby="chain-attack-title">
            <div className="chain-lab__section-heading">
              <div><Flask aria-hidden="true" /><span><strong id="chain-attack-title">안전한 위변조 공격 실험</strong><small>실제 체인과 자산은 변경하지 않습니다.</small></span></div>
              <span className="chain-lab__safe">LOCAL SIMULATION</span>
            </div>

            <div className="chain-lab__attack-flow" aria-label="공격 검증 흐름">
              <FlowStep icon={<Database aria-hidden="true" />} title="1. DB 스냅샷" detail={attackActive ? '블록·수신자·상태 변조됨' : '온체인 값과 동일'} active={attackActive} />
              <span aria-hidden="true">→</span>
              <FlowStep icon={<ArrowClockwise aria-hidden="true" />} title="2. RPC 재조회" detail="같은 txHash로 원본 요청" active={status === 'LOADING'} />
              <span aria-hidden="true">→</span>
              <FlowStep icon={compromised ? <Warning aria-hidden="true" /> : <ShieldCheck aria-hidden="true" />} title="3. 필드 대조" detail={compromised ? `${mismatches.length}개 불일치 탐지` : verified ? '모든 증거 일치' : '재검증 필요'} active={compromised} good={verified} />
            </div>

            <div className="chain-lab__attack-actions">
              <button type="button" className="chain-lab__attack-button" disabled={attackActive || status === 'LOADING'} onClick={runAttack}>
                <Flask aria-hidden="true" /> DB 응답 위변조 시뮬레이션
              </button>
              <button type="button" className="chain-lab__verify-button" disabled={!attackActive || status === 'LOADING'} onClick={() => void inspect()}>
                <ShieldCheck aria-hidden="true" /> RPC 원본과 재검증
              </button>
              {attackActive ? <button type="button" className="chain-lab__restore-button" onClick={restore}>스냅샷 복원</button> : null}
            </div>

            {verified ? (
              <div className="chain-lab__verdict is-verified" role="status">
                <CheckCircle weight="fill" aria-hidden="true" /><div><strong>무결성 검증 통과</strong><span>보관된 스냅샷과 방금 조회한 블록체인 원본이 모두 같습니다.</span></div>
              </div>
            ) : null}
            {compromised ? (
              <div className="chain-lab__verdict is-compromised" role="alert">
                <Warning weight="fill" aria-hidden="true" /><div><strong>위변조 탐지</strong><span>DB나 화면의 값이 바뀌었지만 블록체인 원본은 그대로입니다.</span></div>
              </div>
            ) : null}

            {compromised ? (
              <div className="chain-lab__diff" aria-label="불일치 상세">
                <div className="chain-lab__diff-head"><span>검증 필드</span><span>변조된 스냅샷</span><span>SSAFY 체인 원본</span></div>
                {mismatches.map((mismatch) => (
                  <div className="chain-lab__diff-row" key={mismatch.field}>
                    <strong>{mismatch.label}</strong><code title={mismatch.stored}>{shorten(mismatch.stored)}</code><code title={mismatch.onChain}>{shorten(mismatch.onChain)}</code>
                  </div>
                ))}
              </div>
            ) : null}
          </section>
        </>
      ) : (
        <div className="chain-lab__empty">
          <ShieldCheck aria-hidden="true" />
          <strong>검증할 트랜잭션을 입력해 주세요</strong>
          <span>소재 발행, 출처 검증, 거래 또는 생산 등록에서 생성된 txHash를 사용할 수 있습니다.</span>
        </div>
      )}
    </section>
  )
}

function Metric({ label, value, tone }: { label: string; value: string; tone?: 'good' | 'danger' }) {
  return <article className={tone ? `is-${tone}` : undefined}><span>{label}</span><strong>{value}</strong></article>
}

function EvidenceRow({ label, value, wide = false }: { label: string; value: string; wide?: boolean }) {
  return <div className={wide ? 'is-wide' : undefined}><dt>{label}</dt><dd title={value}>{value}</dd></div>
}

function FlowStep({ icon, title, detail, active = false, good = false }: { icon: ReactNode; title: string; detail: string; active?: boolean; good?: boolean }) {
  return <div className={`${active ? 'is-active' : ''}${good ? ' is-good' : ''}`}>{icon}<span><strong>{title}</strong><small>{detail}</small></span></div>
}

function formatNumber(value: number): string {
  return value.toLocaleString('ko-KR')
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'medium' }).format(new Date(value))
}

function formatTime(value: string): string {
  return new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(new Date(value))
}

function formatUnixTime(value: string): string {
  return formatTime(value)
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
  return blocks.flatMap((block) => block.transactions.map((transaction) => ({ transaction, block }))).slice(0, 12)
}

function shorten(value: string): string {
  return value.length > 30 ? `${value.slice(0, 14)}…${value.slice(-10)}` : value
}
