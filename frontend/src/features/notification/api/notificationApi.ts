import { apiClient } from '../../../services/apiClient.ts'
import type {
  BusinessNotification,
  NotificationPage,
  NotificationReadAllResult,
} from '../types/notification.types.ts'

export const notificationApi = {
  list: async (page = 0, size = 20) => (
    await apiClient.get<NotificationPage>('/v1/notifications', { params: { page, size } })
  ).data,
  read: async (notificationId: number) => (
    await apiClient.patch<BusinessNotification>(`/v1/notifications/${notificationId}/read`)
  ).data,
  readAll: async () => (
    await apiClient.patch<NotificationReadAllResult>('/v1/notifications/read-all')
  ).data,
}
