import { apiClient } from '../apiClient.ts'
import type { ContractTransactionResponse } from 'ethers'

export type BlockchainContract =
  | 'MATERIAL_TOKEN'
  | 'MARKETPLACE'
  | 'PROCESSING_REGISTRY'
  | 'PRODUCTION_REGISTRY'
export type BlockchainReferenceType = 'MATERIAL' | 'TRADE' | 'PROCESSING' | 'PRODUCTION'

export interface BlockchainOperationSpec {
  contract: BlockchainContract
  functionName: string
  referenceType: BlockchainReferenceType
  referenceId: number
}

export interface BlockchainOperation {
  operationId: string
  status: string
  txHash: string | null
}

export async function createBlockchainOperation(
  spec: BlockchainOperationSpec,
): Promise<BlockchainOperation> {
  return (await apiClient.post<BlockchainOperation>('/v1/blockchain/operations', spec)).data
}

export async function submitBlockchainTransaction(operationId: string, txHash: string): Promise<void> {
  await apiClient.put(`/v1/blockchain/operations/${operationId}/transaction`, { txHash })
}

/** 의도 저장 → 지갑 전송 → txHash 저장. 영수증 대기는 호출부가 기존 UX대로 수행한다. */
export async function sendTrackedOperation(
  spec: BlockchainOperationSpec,
  send: (operationId: string) => Promise<ContractTransactionResponse>,
): Promise<ContractTransactionResponse> {
  const operation = await createBlockchainOperation(spec)
  const response = await send(operation.operationId)
  // 이 호출이 실패해도 CREATED 의 operationId가 체인 이벤트에 남으므로 워커가 해시를 복구한다.
  await submitBlockchainTransaction(operation.operationId, response.hash).catch(() => undefined)
  return response
}
