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

export interface PredictionChainState {
  balance: bigint
  totalPool: bigint
  userStake: bigint
  userWinningStake: bigint
  winningPool: bigint
  claimed: boolean
  refundOnly: boolean
}

