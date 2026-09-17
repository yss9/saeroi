import { formatUnits, parseUnits, type ContractTransactionResponse } from 'ethers'
import { contracts } from '../../../config/contracts.ts'
import { getReadContract, getWriteContract } from '../../../services/blockchain/contractFactory.ts'
import type { PredictionChainState, PredictionMarket } from '../types/prediction.types.ts'

const DECIMALS = 18

export function formatPredictionPoint(value: bigint): string {
  return Number(formatUnits(value, DECIMALS)).toLocaleString('ko-KR', { maximumFractionDigits: 4 })
}

export async function loadPredictionChainState(
  market: PredictionMarket,
  walletAddress: string,
): Promise<PredictionChainState> {
  const point = getReadContract('predictionPoint')
  const prediction = getReadContract('predictionMarket')
  const chainMarket = await prediction.getMarket(market.chainMarketId)
  const winningBucket = Number(chainMarket.winningBucket)
  const resolved = Number(chainMarket.status) === 2
  const [balance, userStake, claimed, userWinningStake, winningPool] = await Promise.all([
    point.balanceOf(walletAddress) as Promise<bigint>,
    prediction.userTotalStake(market.chainMarketId, walletAddress) as Promise<bigint>,
    prediction.claimed(market.chainMarketId, walletAddress) as Promise<boolean>,
    resolved
      ? prediction.userBucketStake(market.chainMarketId, walletAddress, winningBucket) as Promise<bigint>
      : Promise.resolve(0n),
    resolved
      ? prediction.bucketPool(market.chainMarketId, winningBucket) as Promise<bigint>
      : Promise.resolve(0n),
  ])
  return {
    balance, userStake, claimed, userWinningStake, winningPool,
    totalPool: chainMarket.totalPool as bigint,
    refundOnly: Boolean(chainMarket.refundOnly),
  }
}

export async function approveAndPlacePredictionBet(
  market: PredictionMarket, bucketIndex: number, pointAmount: string,
): Promise<{ transactionHash: string; stakeAmount: string }> {
  const marketAddress = contracts.predictionMarket
  if (!marketAddress) throw new Error('PredictionMarket 컨트랙트 주소가 설정되지 않았습니다.')
  const amount = parseUnits(pointAmount, DECIMALS)
  if (amount <= 0n) throw new Error('예측 포인트는 0보다 커야 합니다.')

  const point = await getWriteContract('predictionPoint')
  const approve = await point.approve(marketAddress, amount) as ContractTransactionResponse
  await approve.wait()
  const prediction = await getWriteContract('predictionMarket')
  const bet = await prediction.placeBet(market.chainMarketId, bucketIndex, amount) as ContractTransactionResponse
  await bet.wait()
  return { transactionHash: bet.hash, stakeAmount: amount.toString() }
}

export async function claimPrediction(market: PredictionMarket): Promise<string> {
  const prediction = await getWriteContract('predictionMarket')
  const transaction = await prediction.claim(market.chainMarketId) as ContractTransactionResponse
  await transaction.wait()
  return transaction.hash
}

