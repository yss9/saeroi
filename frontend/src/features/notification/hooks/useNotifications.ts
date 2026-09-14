import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { notificationApi } from '../api/notificationApi.ts'

export const notificationKeys = {
  all: ['notifications'] as const,
  list: (page = 0, size = 20) => ['notifications', page, size] as const,
}

export function notificationQueryOptions(enabled: boolean) {
  return {
    queryKey: notificationKeys.list(),
    queryFn: () => notificationApi.list(0, 20),
    enabled,
    refetchInterval: 30_000,
    refetchOnWindowFocus: true,
  }
}

export function useNotifications(enabled: boolean) {
  return useQuery(notificationQueryOptions(enabled))
}

export function useReadNotification() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: notificationApi.read,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: notificationKeys.all }),
  })
}

export function useReadAllNotifications() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: notificationApi.readAll,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: notificationKeys.all }),
  })
}
