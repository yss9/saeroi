export type PredictionReasonCode =
  | 'DESIGN' | 'PRICE' | 'BRAND' | 'SUSTAINABILITY' | 'PRACTICALITY' | 'TREND' | 'OTHER'

export interface PredictionBucket { index: number; lowerBound: number; upperBound: number }

export interface PredictionMarket {
  id: number
  chainMarketId: number
  productionBatchId: number
  productModelId: number
  productName: string
  productThumbnailUrl: string | null
  unitPrice: number
  productionQuantity: number
  brandWallet: string
  bettingEndsAt: string
  salesStartsAt: string
  salesEndsAt: string
  status: 'OPEN' | 'RESOLVING' | 'RESOLVED' | 'CANCELED'
  soldQuantity: number | null
  winningBucketIndex: number | null
  resultHash: string | null
  buckets: PredictionBucket[]
}

export interface PredictionSurveyRequest {
  bucketIndex: number
  stakeAmount: string
  reasonCode: PredictionReasonCode
  reasonText: string
  transactionHash: string
}

export interface PredictionPointGrant { walletAddress: string; amount: string; transactionHash: string }

export interface BrandPredictionBucketInsight extends PredictionBucket {
  responseCount: number
  stakeAmount: string
}

export interface BrandPredictionReasonInsight {
  reasonCode: PredictionReasonCode
  responseCount: number
}

export interface BrandPredictionComment {
  reasonCode: PredictionReasonCode
  reasonText: string
  submittedAt: string
}

export interface BrandPredictionInsight {
  market: PredictionMarket
  participantCount: number
  totalStakeAmount: string
  buckets: BrandPredictionBucketInsight[]
  reasons: BrandPredictionReasonInsight[]
  comments: BrandPredictionComment[]
}

export interface PredictionChainState {
  totalPool: bigint
  userStake: bigint
  userWinningStake: bigint
  winningPool: bigint
  claimed: boolean
  refundOnly: boolean
  /** 구간별로 모인 포인트. 인덱스가 구간 번호다 */
  bucketPools: bigint[]
  /** 내가 구간별로 건 포인트. 어느 구간에 걸었는지 보여 주는 데 쓴다 */
  userBucketStakes: bigint[]
}

