import { useMutation } from '@tanstack/react-query'
import { apiClient } from '@/api/client'

export function useDeleteAccount() {
  return useMutation<void, Error, void>({
    mutationFn: () => apiClient.delete('/me').then(r => r.data),
  })
}
