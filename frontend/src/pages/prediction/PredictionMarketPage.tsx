import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Button, ErrorMessage, Loading } from '../../components/common/index.ts'
import { grantPredictionWelcomePoint } from '../../features/prediction/api/predictionApi.ts'
import { PredictionMarketCard } from '../../features/prediction/components/PredictionMarketCard.tsx'
import { predictionKeys, usePredictionMarkets } from '../../features/prediction/hooks/usePredictionMarkets.ts'
import { predictionPointBalanceKey, usePredictionPointBalance } from '../../features/prediction/hooks/usePredictionPointBalance.ts'
import { formatPredictionPoint } from '../../features/prediction/services/predictionContractService.ts'
import { sortMarketsForConsumer } from '../../features/prediction/utils/marketPhase.ts'
import { ConsumerShopLayout } from '../../features/user/components/ConsumerAccountLayout.tsx'
import { WalletRegistrationPanel } from '../../features/wallet/components/WalletRegistrationPanel.tsx'
import { useMyWallets } from '../../features/wallet/hooks/useMyWallets.ts'
import { useWallet } from '../../features/wallet/hooks/useWallet.ts'
import { toErrorMessage } from '../../utils/errors.ts'
import './prediction-market.css'

const HOW_IT_WORKS = [
  ['판매량 구간 고르기', '확정된 생산량 안에서 실제로 몇 개가 팔릴지 고릅니다.'],
  ['포인트 걸기', '받은 예측 포인트를 겁니다. 시장마다 한 번 참여합니다.'],
  ['맞히면 나눠 받기', '판매가 끝나면 결제·취소를 반영한 순판매량으로 판정합니다.'],
] as const

/**
 * 판매량 예측시장.
 *
 * 처음 온 사람이 할 일은 순서대로 셋이다 — 지갑 연결, 포인트 받기, 참여할 시장 고르기. 위에서
 * 아래로 그 순서대로 둔다. 참여할 수 있는 시장과 지난 시장은 나눠 보인다.
 */
export function PredictionMarketPage() {
  const wallet = useWallet()
  const myWallets = useMyWallets()
  const markets = usePredictionMarkets()
  const queryClient = useQueryClient()
  const registeredAddress = myWallets.activeWallet?.walletAddress ?? null
  const usableWallet = wallet.status === 'connected' && !wallet.wrongChain
    && registeredAddress?.toLowerCase() === wallet.address?.toLowerCase() ? wallet.address : null
  // 열린 시장이 하나도 없어도 잔액은 보여야 한다. 그래서 시장과 따로 읽는다.
  const pointBalance = usePredictionPointBalance(usableWallet)
  const refreshBalance = () => void queryClient.invalidateQueries({ queryKey: predictionPointBalanceKey(usableWallet) })
  const welcome = useMutation({ mutationFn: grantPredictionWelcomePoint, onSuccess: refreshBalance })
  const welcomeAlreadyReceived = Boolean(welcome.data) || Boolean(welcome.error && toErrorMessage(welcome.error).includes('이미 지급'))

  // 남은 시간과 마감 여부가 화면을 연 채로도 바뀌어야 한다. 시장마다 타이머를 두지 않고 하나로 돈다.
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 30_000)
    return () => window.clearInterval(timer)
  }, [])

  const { open, past } = sortMarketsForConsumer(markets.data ?? [], now)
  const onChanged = () => {
    refreshBalance()
    void queryClient.invalidateQueries({ queryKey: predictionKeys.all })
  }
  const card = (market: (typeof open)[number]) => (
    <PredictionMarketCard
      key={market.id}
      market={market}
      walletAddress={usableWallet}
      balance={pointBalance.data}
      now={now}
      onChanged={onChanged}
    />
  )

  return (
    <ConsumerShopLayout>
      <main className="consumer-mypage-main prediction-page">
        <section className="prediction-intro">
          <p className="prediction-eyebrow">PREDICTION</p>
          <h2>판매량 예측시장</h2>
          <p>새로 생산된 제품이 얼마나 팔릴지 예측하고, 맞히면 포인트를 나눠 받습니다.</p>
          <ol className="prediction-steps">
            {HOW_IT_WORKS.map(([title, body], index) => (
              <li key={title}><span>{index + 1}</span><strong>{title}</strong><small>{body}</small></li>
            ))}
          </ol>
        </section>

        <section className="prediction-account" aria-label="내 지갑과 포인트">
          <WalletRegistrationPanel registeredAddress={registeredAddress} />
          {usableWallet ? (
            <div className="prediction-balance">
              <div>
                <span>내 예측 포인트</span>
                <strong>{pointBalance.data !== undefined ? formatPredictionPoint(pointBalance.data) : '—'}</strong>
              </div>
              {/* 포인트가 있으면 받을 일이 없다. 0 일 때만 권한다 — 이미 받은 사람에게 늘 보이면 누를 때마다 거절당한다. */}
              {(pointBalance.data === 0n || welcome.data || welcome.error) && (
                <Button variant="secondary" onClick={() => welcome.mutate()} loading={welcome.isPending} disabled={welcomeAlreadyReceived}>
                  {welcomeAlreadyReceived ? '참여 포인트 지급 완료' : '첫 참여 포인트 받기'}
                </Button>
              )}
            </div>
          ) : (
            <p className="prediction-hint">등록한 지갑을 연결하면 포인트를 받고 예측에 참여할 수 있습니다.</p>
          )}
          {welcome.data ? <p className="prediction-success">참여 포인트가 지급되었습니다. 아래에서 예측에 참여해 보세요.</p> : null}
          {welcome.error ? <p className={welcomeAlreadyReceived ? 'prediction-notice' : 'prediction-error'}>{welcomeAlreadyReceived ? '참여 포인트를 이미 받았습니다.' : toErrorMessage(welcome.error)}</p> : null}
        </section>

        {markets.isPending ? <Loading /> : markets.isError ? <ErrorMessage error={markets.error} /> : (
          <>
            <section className="prediction-section" aria-label="참여할 수 있는 예측">
              <h3>참여할 수 있는 예측 <span>{open.length}</span></h3>
              {open.length > 0
                ? <div className="prediction-grid">{open.map(card)}</div>
                : <p className="prediction-empty">지금 참여할 수 있는 예측이 없습니다. 새 예측이 열리면 여기에 보입니다.</p>}
            </section>
            {past.length > 0 && (
              <section className="prediction-section" aria-label="지난 예측">
                <h3>지난 예측 <span>{past.length}</span></h3>
                <div className="prediction-grid">{past.map(card)}</div>
              </section>
            )}
          </>
        )}
      </main>
    </ConsumerShopLayout>
  )
}
