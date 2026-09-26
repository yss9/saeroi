import type { MaterialType } from '../../material/types/material.types.ts'

export type { MaterialType } from '../../material/types/material.types.ts'

export type BlockchainTxStatus = 'PENDING' | 'SUCCESS' | 'FAILED'
export type PassportItemStatus = 'IN_STOCK' | 'RESERVED' | 'SOLD'
export type JourneyStageType = 'SOURCE' | 'COLLECTION' | 'PROCESSING' | 'BRAND' | 'DELIVERY'
export type JourneyStageStatus = 'COMPLETED' | 'CURRENT' | 'PENDING'

export type CompanyRole = 'SUPPLIER' | 'PROCESSOR' | 'BRAND' | 'CONSUMER' | 'ADMIN'

export interface PassportProduct {
  name: string
  brandName: string | null
  category: 'BAG' | 'WALLET' | 'CLOTHING' | 'ACCESSORY' | 'HOME' | 'ETC'
  description: string
  imageUrl: string
}

export interface JourneyStage {
  stage: JourneyStageType
  sequence: number
  status: JourneyStageStatus
  nodeIds: string[]
  edgeIds: string[]
}

export interface JourneyMaterial {
  name: string
  materialType: MaterialType
  rootOrigin: 'VIRGIN' | 'WASTE'
  batchQuantityGrams: number
}

export interface BlockchainProof {
  referenceType: 'MATERIAL' | 'TRADE' | 'PROCESSING' | 'PRODUCTION'
  chainId: number
  contractAddress: string
  functionName: string
  txHash: string
  blockNumber: number | null
  status: BlockchainTxStatus
  confirmedAt: string | null
}

export interface EvidenceCount {
  evidenceType:
    | 'BEFORE_PHOTO'
    | 'AFTER_PHOTO'
    | 'WEIGHT_SLIP'
    | 'INVOICE'
    | 'PROCESS_PHOTO'
    | 'ETC'
  count: number
}

export interface JourneyNode {
  nodeId: string
  stage: JourneyStageType
  companyName: string | null
  companyRole: CompanyRole | null
  locationName: string | null
  latitude: number | null
  longitude: number | null
  startedAt: string | null
  completedAt: string | null
  status: string
  description: string | null
  processTypes: string[]
  materials: JourneyMaterial[]
  blockchainProofs: BlockchainProof[]
  evidenceSummary: EvidenceCount[]
}

export interface JourneyEdge {
  edgeId: string
  fromNodeId: string
  toNodeId: string
  stage: JourneyStageType
  materialName: string | null
  materialType: MaterialType | null
  shippedAt: string | null
  receivedAt: string | null
  status: JourneyStageStatus
}

export interface ProductJourney {
  stages: JourneyStage[]
  nodes: JourneyNode[]
  edges: JourneyEdge[]
  materialComposition: Array<{ fiberType: string; percentage: number }>
  upcycleRatio: number
  blockchainVerified: boolean
  truncated: boolean
  message: string | null
}

export interface ProductPassport {
  itemCode: string
  serialNumber: number
  itemStatus: PassportItemStatus
  issuedAt: string
  product: PassportProduct
  traceabilityAvailable: boolean
  journey: ProductJourney | null
}
