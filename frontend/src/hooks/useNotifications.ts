import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { notificationsApi } from '@/api/notifications'

export function useNotifications(page = 0) {
  return useQuery({
    queryKey: ['notifications', page],
    queryFn: () => notificationsApi.list(page).then(r => r.data),
  })
}

export function useUnreadCount() {
  return useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: () => notificationsApi.unreadCount().then(r => r.data.count),
    refetchInterval: 30000,
  })
}

export function useMarkRead() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => notificationsApi.markRead(id).then(r => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['notifications'] })
    },
  })
}
