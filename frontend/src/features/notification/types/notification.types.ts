export type NotificationType =
  | 'PROCESSING_REQUEST_RECEIVED'
  | 'PROCESSING_REQUEST_CANCELED'
  | 'PROCESSING_REQUEST_REJECTED'
  | 'PROCESSING_REQUEST_EXPIRED'
  | 'PROCESSING_QUOTE_RECEIVED'
  | 'PROCESSING_QUOTE_SELECTED'
  | 'PROCESSING_PAYMENT_COMPLETED'
  | 'PROCESSING_MATERIAL_SHIPPED'
  | 'PROCESSING_MATERIAL_RECEIVED'
  | 'PROCESSING_STARTED'
  | 'PROCESSING_RESULT_SUBMITTED'
  | 'PROCESSING_RESULT_APPROVED'
  | 'PROCESSING_RESULT_REJECTED'
  | 'PROCESSING_JOB_EXPIRED'
  | 'TRADE_CREATED'
  | 'TRADE_PAID'
  | 'TRADE_SHIPPED'
  | 'TRADE_RECEIVED'
  | 'TRADE_CANCELED'
  | 'PRODUCT_ORDER_CREATED'
  | 'PRODUCT_ORDER_PAID'
  | 'PRODUCT_ORDER_CANCELED'
  | 'PRODUCT_ORDER_SHIPPED'

export type NotificationReferenceType =
  | 'PROCESSING_REQUEST'
  | 'PROCESSING_JOB'
  | 'MATERIAL_TRADE'
  | 'PRODUCT_ORDER'

export interface BusinessNotification {
  notificationId: number
  type: NotificationType
  title: string
  message: string
  targetPath: string
  referenceType: NotificationReferenceType
  referenceId: number
  read: boolean
  createdAt: string
}

export interface NotificationPage {
  items: BusinessNotification[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  unreadCount: number
}

export interface NotificationReadAllResult {
  updatedCount: number
}
