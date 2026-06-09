import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { projectsApi } from '@/api/projects'

export function useProjects() {
  return useQuery({
    queryKey: ['projects'],
    queryFn: () => projectsApi.list().then(r => r.data)
  })
}

export function useProject(key: string) {
  return useQuery({
    queryKey: ['projects', key],
    queryFn: () => projectsApi.get(key).then(r => r.data)
  })
}

export function useCreateProject() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: { key: string; name: string; description?: string }) =>
      projectsApi.create(data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['projects'] })
  })
}
