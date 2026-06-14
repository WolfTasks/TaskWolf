import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { workflowEditorApi } from '../api/workflowEditor'
import type { TransitionGuard, StatusPosition } from '../types'

const key = (projectKey: string) => ['workflow-editor', projectKey]

export function useWorkflowEditor(projectKey: string) {
  return useQuery({ queryKey: key(projectKey), queryFn: () => workflowEditorApi.get(projectKey) })
}

export function useCreateStatus(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ name, category, color }: { name: string; category: string; color: string }) =>
      workflowEditorApi.createStatus(projectKey, name, category, color),
    onSuccess: () => qc.invalidateQueries({ queryKey: key(projectKey) }),
  })
}

export function useUpdateStatus(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ sid, data }: { sid: string; data: { name?: string; category?: string; color?: string } }) =>
      workflowEditorApi.updateStatus(projectKey, sid, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: key(projectKey) }),
  })
}

export function useDeleteStatus(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (sid: string) => workflowEditorApi.deleteStatus(projectKey, sid),
    onSuccess: () => qc.invalidateQueries({ queryKey: key(projectKey) }),
  })
}

export function useCreateTransition(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ fromStatusId, toStatusId }: { fromStatusId: string | null; toStatusId: string }) =>
      workflowEditorApi.createTransition(projectKey, fromStatusId, toStatusId),
    onSuccess: () => qc.invalidateQueries({ queryKey: key(projectKey) }),
  })
}

export function useDeleteTransition(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (tid: string) => workflowEditorApi.deleteTransition(projectKey, tid),
    onSuccess: () => qc.invalidateQueries({ queryKey: key(projectKey) }),
  })
}

export function useUpdateGuards(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ tid, guards }: { tid: string; guards: TransitionGuard[] }) =>
      workflowEditorApi.updateGuards(projectKey, tid, guards),
    onSuccess: () => qc.invalidateQueries({ queryKey: key(projectKey) }),
  })
}

export function useSaveLayout(projectKey: string) {
  return useMutation({
    mutationFn: (positions: StatusPosition[]) => workflowEditorApi.saveLayout(projectKey, positions),
  })
}
