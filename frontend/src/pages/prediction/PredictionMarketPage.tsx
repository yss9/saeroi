import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState, type FormEvent } from 'react'
import { Button, ErrorMessage, Loading } from '../../components/common/index.ts'
import { grantPredictionWelcomePoint, savePredictionSurvey } from '../../features/prediction/api/predictionApi.ts'
import { usePredictionChainState } from '../../features/prediction/hooks/usePredictionChainState.ts'
import { predictionKeys, usePredictionMarkets } from '../../features/prediction/hooks/usePredictionMarkets.ts'
import { predictionPointBalanceKey, usePredictionPointBalance } from '../../features/prediction/hooks/usePredictionPointBalance.ts'
import { approveAndPlacePredictionBet, claimPrediction, formatPredictionPoint } from '../../features/prediction/services/predictionContractService.ts'
import type { PredictionMarket, PredictionReasonCode } from '../../features/prediction/types/prediction.types.ts'
import { ConsumerShopLayout } from '../../features/user/components/ConsumerAccountLayout.tsx'
import { WalletRegistrationPanel } from '../../features/wallet/components/WalletRegistrationPanel.tsx'
import { useMyWallets } from '../../features/wallet/hooks/useMyWallets.ts'
import { useWallet } from '../../features/wallet/hooks/useWallet.ts'
import { toErrorMessage } from '../../utils/errors.ts'
import './prediction-market.css'

const REASONS: { value: PredictionReasonCode; label: string }[] = [
  { value: 'DESIGN', label: '디자인' }, { value: 'PRICE', label: '가격' },
  { value: 'BRAND', label: '브랜드' }, { value: 'SUSTAINABILITY', label: '친환경 가치' },
  { value: 'PRACTICALITY', label: '실용성' }, { value: 'TREND', label: '트렌드' },
  { value: 'OTHER', label: '기타' },
]

function formatDate(value: string) {
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function formatPredictionProfit(value: bigint) {
  return `${value > 0n ? '+' : ''}${formatPredictionPoint(value)}`
}

function PredictionResult({ soldQuantity, bucketIndex }: { soldQuantity: number; bucketIndex: number }) {
  return (
    <p
      className="prediction-card__result"
      aria-label={`최종 순판매량 ${soldQuantity}개, 당첨 구간 ${bucketIndex + 1}`}
    >
      최종 순판매량 <strong>{soldQuantity.toLocaleString()}</strong>개 · 당첨 구간 #{bucketIndex + 1}
    </p>
  )
}

function MarketCard({ market, walletAddress }: { market: PredictionMarket; walletAddress: string | null }) {
  const [bucket, setBucket] = useState(0)
  const [amount, setAmount] = useState('10')
  const [reasonCode, setReasonCode] = useState<PredictionReasonCode>('DESIGN')
  const [reasonText, setReasonText] = useState('')
  const [notice, setNotice] = useState<string | null>(null)
  const [resultRevealed, setResultRevealed] = useState(false)
  const [now, setNow] = useState(0)
  useEffect(() => {
    const update = () => setNow(Date.now())
    update()
    const timer = window.setInterval(update, 30_000)
    return () => window.clearInterval(timer)
  }, [])
  const chain = usePredictionChainState(market, walletAddress)
  const queryClient = useQueryClient()
  const bet = useMutation({
    mutationFn: async () => {
      const result = await approveAndPlacePredictionBet(market, bucket, amount)
      await savePredictionSurvey(market.id, {
        bucketIndex: bucket, stakeAmount: result.stakeAmount, reasonCode, reasonText,
        transactionHash: result.transactionHash,
      })
      return result
    },
    onSuccess: (result) => {
      setNotice(`예측과 선호 이유가 기록되었습니다. (${result.transactionHash.slice(0, 12)}…)`)
      void chain.refetch()
      void queryClient.invalidateQueries({ queryKey: predictionKeys.all })
    },
  })
  const claim = useMutation({
    mutationFn: () => claimPrediction(market),
    onSuccess: (hash) => { setNotice(`보상 수령이 완료되었습니다. (${hash.slice(0, 12)}…)`); void chain.refetch() },
  })

  const isOpen = market.status === 'OPEN' && now < new Date(market.bettingEndsAt).getTime()
  const state = chain.data
  const expected = state && state.winningPool > 0n
    ? state.totalPool * state.userWinningStake / state.winningPool
    : state?.refundOnly ? state.userStake : 0n
  const profit = state && market.status === 'RESOLVED'
    ? expected - state.userStake
    : state && market.status === 'CANCELED' ? 0n : null
  const canClaim = Boolean(state && !state.claimed && state.userStake > 0n
    && (market.status === 'CANCELED' || market.status === 'RESOLVED')
    && (state.refundOnly || state.userWinningStake > 0n))

  return (
    <article className="prediction-card">
      <div className="prediction-card__heading">
        <div><span>{market.status}</span><h3>{market.productName}</h3></div>
        <strong>생산 {market.productionQuantity.toLocaleString()}개</strong>
      </div>
      <p>판매 집계: {formatDate(market.salesStartsAt)} ~ {formatDate(market.salesEndsAt)}</p>
      <p>예측 마감: {formatDate(market.bettingEndsAt)}</p>
      {market.soldQuantity !== null && !resultRevealed ? (
        <Button variant="secondary" onClick={() => setResultRevealed(true)}>결과 확인하기</Button>
      ) : null}
      {market.soldQuantity !== null && resultRevealed ? (
        <PredictionResult
          key={`${market.id}-${market.soldQuantity}`}
          soldQuantity={market.soldQuantity}
          bucketIndex={market.winningBucketIndex ?? 0}
        />
      ) : null}

      {state ? (
        <div className="prediction-card__wallet-stats">
          <span>내 예치 {formatPredictionPoint(state.userStake)} PRED</span>
          <span>내 수익 {profit === null ? '정산 전' : `${formatPredictionProfit(profit)} PRED`}</span>
        </div>
      ) : null}

      {isOpen ? (
        <form onSubmit={(event: FormEvent) => { event.preventDefault(); setNotice(null); bet.mutate() }}>
          <fieldset className="prediction-buckets"><legend>예상 판매량 구간</legend>
            {market.buckets.map((item) => (
              <label key={item.index}>
                <input type="radio" name={`bucket-${market.id}`} checked={bucket === item.index} onChange={() => setBucket(item.index)} />
                {item.lowerBound}~{item.upperBound}개
              </label>
            ))}
          </fieldset>
          <label>예치 포인트<input value={amount} onChange={(event) => setAmount(event.target.value)} inputMode="decimal" required /></label>
          <label>그렇게 예상한 가장 큰 이유<select value={reasonCode} onChange={(event) => setReasonCode(event.target.value as PredictionReasonCode)}>
            {REASONS.map((reason) => <option key={reason.value} value={reason.value}>{reason.label}</option>)}
          </select></label>
          <label>이유 설명<textarea value={reasonText} onChange={(event) => setReasonText(event.target.value)} maxLength={500} placeholder="디자인, 가격, 소재 등 판단 근거를 알려주세요." /></label>
          <Button type="submit" loading={bet.isPending} disabled={!walletAddress}>approve 후 예측하기</Button>
          {bet.error ? <p className="prediction-error">{toErrorMessage(bet.error)}</p> : null}
        </form>
      ) : null}

      {canClaim ? <Button onClick={() => claim.mutate()} loading={claim.isPending}>
        {state?.refundOnly ? '예치 포인트 환불받기' : `예상 보상 ${formatPredictionPoint(expected)} PRED 받기`}
      </Button> : null}
      {state?.claimed ? <p className="prediction-success">보상 수령 완료</p> : null}
      {claim.error ? <p className="prediction-error">{toErrorMessage(claim.error)}</p> : null}
      {notice ? <p className="prediction-success">{notice}</p> : null}
    </article>
  )
}

export function PredictionMarketPage() {
  const wallet = useWallet()
  const myWallets = useMyWallets()
  const markets = usePredictionMarkets()
  const queryClient = useQueryClient()
  const registeredAddress = myWallets.activeWallet?.walletAddress ?? null
  const usableWallet = wallet.status === 'connected' && !wallet.wrongChain
    && registeredAddress?.toLowerCase() === wallet.address?.toLowerCase() ? wallet.address : null
  // 열린 시장이 하나도 없으면 시장 카드 안의 잔액 표시가 뜰 자리가 없다.
  // 그래서 시장 유무와 무관하게 잔액만 따로 조회한다.
  const pointBalance = usePredictionPointBalance(usableWallet)
  const welcome = useMutation({
    mutationFn: grantPredictionWelcomePoint,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: predictionPointBalanceKey(usableWallet) }),
  })
  const welcomeAlreadyReceived = Boolean(welcome.data) || Boolean(welcome.error && toErrorMessage(welcome.error).includes('이미 지급'))

  return (
    <ConsumerShopLayout>
      <main className="consumer-mypage-main prediction-page">
      <section className="consumer-mypage-card prediction-intro">
        <p className="consumer-section-heading__eyebrow">ON-CHAIN PREDICTION</p>
        <h2>판매량 예측시장</h2>
        <p>생산량은 체인에 고정되고, 실제 결제·취소에서 계산한 순판매량으로 결과를 판정합니다.<br />선택한 이유는 브랜드의 선호도 분석에 익명 집계됩니다.</p>
        <WalletRegistrationPanel registeredAddress={registeredAddress} />
        {usableWallet ? (
          <div className="prediction-card__wallet-stats">
            <span>보유 {pointBalance.data !== undefined ? formatPredictionPoint(pointBalance.data) : '—'} PRED</span>
          </div>
        ) : null}
        {usableWallet ? <Button variant="secondary" onClick={() => welcome.mutate()} loading={welcome.isPending} disabled={welcomeAlreadyReceived}>{welcomeAlreadyReceived ? '참여 포인트 지급 완료' : '첫 참여 포인트 받기'}</Button> : null}
        {welcome.data ? <p className="prediction-success">웰컴 포인트가 지급되었습니다.</p> : null}
        {welcome.error ? <p className={welcomeAlreadyReceived ? 'prediction-notice' : 'prediction-error'}>{welcomeAlreadyReceived ? '참여 포인트를 이미 받았습니다.' : toErrorMessage(welcome.error)}</p> : null}
      </section>

      {markets.isPending ? <Loading /> : markets.isError ? <ErrorMessage error={markets.error} /> : (
        <section className="prediction-grid" aria-label="예측시장 목록">
          {(markets.data ?? []).map((market) => <MarketCard key={market.id} market={market} walletAddress={usableWallet} />)}
          {markets.data?.length === 0 ? <p>현재 열린 예측시장이 없습니다.</p> : null}
        </section>
      )}
      </main>
    </ConsumerShopLayout>
  )
}
