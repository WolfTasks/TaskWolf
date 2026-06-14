import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { automationApi } from '../api/automation'
import type { AutomationRuleDraft } from '../types'

export function useAutomationRules(projectKey: string) {
  return useQuery({ queryKey: ['automation', projectKey], queryFn: () => automationApi.list(projectKey) })
}

export function useCreateRule(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (draft: AutomationRuleDraft) => automationApi.create(projectKey, draft),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['automation', projectKey] }),
  })
}

export function useToggleRule(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (rid: string) => automationApi.toggle(projectKey, rid),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['automation', projectKey] }),
  })
}

export function useDeleteRule(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (rid: string) => automationApi.delete(projectKey, rid),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['automation', projectKey] }),
  })
}

export function useSystemRules() {
  return useQuery({ queryKey: ['automation', 'system'], queryFn: () => automationApi.listSystem() })
}

export function useCreateSystemRule() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (draft: AutomationRuleDraft) => automationApi.createSystem(draft),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['automation', 'system'] }),
  })
}

export function useToggleSystemRule() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (rid: string) => automationApi.toggleSystem(rid),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['automation', 'system'] }),
  })
}
