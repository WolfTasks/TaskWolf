import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/api/client'

export interface AdminUser {
  id: string
  email: string
  displayName: string
  systemRole: 'ADMIN' | 'MEMBER'
  active: boolean
}

export function useAdminUsers() {
  return useQuery<AdminUser[]>({
    queryKey: ['admin-users'],
    queryFn: () => apiClient.get('/admin/users').then(r => r.data),
  })
}

export function useDeactivateUser() {
  const qc = useQueryClient()
  return useMutation<void, Error, string>({
    mutationFn: id => apiClient.post(`/admin/users/${id}/deactivate`).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-users'] }),
  })
}

export function useActivateUser() {
  const qc = useQueryClient()
  return useMutation<void, Error, string>({
    mutationFn: id => apiClient.post(`/admin/users/${id}/activate`).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-users'] }),
  })
}

export function useDeleteUser() {
  const qc = useQueryClient()
  return useMutation<void, Error, string>({
    mutationFn: id => apiClient.delete(`/admin/users/${id}`).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-users'] }),
  })
}
