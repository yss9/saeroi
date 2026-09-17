export type CancellationKind = 'CANCEL' | 'RETURN' | 'EXCHANGE' | 'REFUND'
export type CancellationStatus = 'REQUESTED' | 'PROCESSING' | 'DONE' | 'REJECTED'

export interface CancellationItem {
  id: string
  kind: CancellationKind
  status: CancellationStatus
  productName: string
  orderNumber: string
  orderedAt: string
  requestedAt: string
  itemCount: number
  reason: string
  amount: number
}
