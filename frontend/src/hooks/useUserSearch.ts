import { useQuery } from '@tanstack/react-query'
import { usersApi } from '@/api/users'

export function useUserSearch(query: string) {
  const q = query.trim()
  return useQuery({
    queryKey: ['user-search', q],
    queryFn: () => usersApi.search(q).then(r => r.data),
    enabled: q.length >= 2,
  })
}
