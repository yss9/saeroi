import { formatUnits, parseUnits, type ContractTransactionResponse } from 'ethers'
import { contracts } from '../../../config/contracts.ts'
import { getReadContract, getWriteContract } from '../../../services/blockchain/contractFactory.ts'
import type { PredictionChainState, PredictionMarket } from '../types/prediction.types.ts'

const DECIMALS = 18

export function formatPredictionPoint(value: bigint): string {
  return Number(formatUnits(value, DECIMALS)).toLocaleString('ko-KR', { maximumFractionDigits: 4 })
}

/**
 * 지금 보유한 PRED 포인트만 조회한다.
 *
 * `loadPredictionChainState`는 특정 시장의 예치 현황까지 같이 조회하므로 시장이
 * 하나도 없으면(예: 아직 아무도 열지 않은 초기 상태) 부를 곳이 없다. 열린 시장
 * 유무와 무관하게 "받은 포인트가 잔액에 잡혔는지"만 보여줘야 하는 화면(웰컴 포인트
 * 발급 직후 등)을 위해 잔액만 따로 뗐다.
 */
export async function loadPredictionPointBalance(walletAddress: string): Promise<bigint> {
  const point = getReadContract('predictionPoint')
  return point.balanceOf(walletAddress) as Promise<bigint>
}

export async function loadPredictionChainState(
  market: PredictionMarket,
  walletAddress: string,
): Promise<PredictionChainState> {
  const prediction = getReadContract('predictionMarket')
  const chainMarket = await prediction.getMarket(market.chainMarketId)
  const winningBucket = Number(chainMarket.winningBucket)
  const resolved = Number(chainMarket.status) === 2
  const indexes = market.buckets.map((bucket) => bucket.index)
  // 구간 수는 스무 개를 넘지 않는다(서버 검증). 구간마다 두 번씩 읽어도 한 번에 끝난다.
  const [userStake, claimed, bucketPools, userBucketStakes] = await Promise.all([
    prediction.userTotalStake(market.chainMarketId, walletAddress) as Promise<bigint>,
    prediction.claimed(market.chainMarketId, walletAddress) as Promise<boolean>,
    Promise.all(indexes.map((index) => prediction.bucketPool(market.chainMarketId, index) as Promise<bigint>)),
    Promise.all(indexes.map((index) =>
      prediction.userBucketStake(market.chainMarketId, walletAddress, index) as Promise<bigint>)),
  ])
  return {
    userStake, claimed, bucketPools, userBucketStakes,
    userWinningStake: resolved ? userBucketStakes[winningBucket] ?? 0n : 0n,
    winningPool: resolved ? bucketPools[winningBucket] ?? 0n : 0n,
    totalPool: chainMarket.totalPool as bigint,
    refundOnly: Boolean(chainMarket.refundOnly),
  }
}

/** 참여는 지갑 서명이 두 번이다 — 포인트 사용 승인(approve), 예측 기록(placeBet). */
export type PredictionBetStep = 'APPROVE' | 'PLACE_BET'

export async function approveAndPlacePredictionBet(
  market: PredictionMarket, bucketIndex: number, pointAmount: string,
  onStep: (step: PredictionBetStep) => void = () => {},
): Promise<{ transactionHash: string; stakeAmount: string }> {
  const marketAddress = contracts.predictionMarket
  if (!marketAddress) throw new Error('예측시장 기능을 사용할 수 없습니다. 관리자에게 문의해 주세요.')
  const amount = parseUnits(pointAmount, DECIMALS)
  if (amount <= 0n) throw new Error('예측 포인트는 0보다 커야 합니다.')

  onStep('APPROVE')
  const point = await getWriteContract('predictionPoint')
  const approve = await point.approve(marketAddress, amount) as ContractTransactionResponse
  await approve.wait()
  onStep('PLACE_BET')
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

