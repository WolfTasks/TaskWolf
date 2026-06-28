import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { issuesApi } from '@/api/issues'

interface IssueListOpts {
  labelId?: string
  fixVersionId?: string
  affectsVersionId?: string
  customFieldFilters?: Record<string, string>
}

export function useIssues(projectKey: string, opts: IssueListOpts = {}) {
  const { labelId, fixVersionId, affectsVersionId, customFieldFilters } = opts
  return useQuery({
    queryKey: ['issues', projectKey, { labelId, fixVersionId, affectsVersionId, customFieldFilters }],
    queryFn: () => issuesApi.list(projectKey, 0, 50, labelId, fixVersionId, affectsVersionId, customFieldFilters).then(r => r.data)
  })
}

export function useIssue(projectKey: string, issueKey: string) {
  return useQuery({
    queryKey: ['issues', projectKey, issueKey],
    queryFn: () => issuesApi.get(projectKey, issueKey).then(r => r.data)
  })
}

export function useCreateIssue(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: { title: string; type?: string; priority?: string; description?: string; customFieldValues?: { fieldId: string; value: string | null }[] }) =>
      issuesApi.create(projectKey, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['issues', projectKey] })
  })
}

export function useUpdateIssue(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: Record<string, unknown> }) =>
      issuesApi.update(projectKey, id, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['issues', projectKey] })
  })
}
