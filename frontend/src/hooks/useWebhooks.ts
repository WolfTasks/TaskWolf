import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/api/client'

export interface WebhookItem {
  id: string
  url: string
  events: string[]
  enabled: boolean
  createdAt: string | null
}

export interface WebhookDelivery {
  id: string
  webhookId: string
  eventType: string
  payload: string
  responseStatus: number | null
  responseBody: string | null
  attemptCount: number
  deliveredAt: string | null
  createdAt: string | null
}

export interface CreateWebhookResult {
  webhook: WebhookItem
  plaintextSecret: string
}

export const ALL_WEBHOOK_EVENTS = [
  'issue.created', 'issue.updated', 'issue.status_changed',
  'issue.assigned', 'issue.deleted', 'sprint.started',
  'sprint.completed', 'comment.created', 'attachment.added',
]

export function useWebhooks(projectKey: string) {
  return useQuery<WebhookItem[]>({
    queryKey: ['webhooks', projectKey],
    queryFn: () => apiClient.get(`/projects/${projectKey}/webhooks`).then(r => r.data),
  })
}

export function useCreateWebhook(projectKey: string) {
  const qc = useQueryClient()
  return useMutation<CreateWebhookResult, Error, { url: string; events: string[]; secret?: string }>({
    mutationFn: body => apiClient.post(`/projects/${projectKey}/webhooks`, body).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['webhooks', projectKey] }),
  })
}

export function useDeleteWebhook(projectKey: string) {
  const qc = useQueryClient()
  return useMutation<void, Error, string>({
    mutationFn: id => apiClient.delete(`/projects/${projectKey}/webhooks/${id}`).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['webhooks', projectKey] }),
  })
}

export function useWebhookDeliveries(projectKey: string, webhookId: string | null) {
  return useQuery<WebhookDelivery[]>({
    queryKey: ['webhook-deliveries', projectKey, webhookId],
    queryFn: () => apiClient.get(`/projects/${projectKey}/webhooks/${webhookId}/deliveries`).then(r => r.data),
    enabled: !!webhookId,
  })
}

export function useTestPing(projectKey: string) {
  const qc = useQueryClient()
  return useMutation<WebhookDelivery, Error, string>({
    mutationFn: webhookId => apiClient.post(`/projects/${projectKey}/webhooks/${webhookId}/test`, {}).then(r => r.data),
    onSuccess: (_, webhookId) => qc.invalidateQueries({ queryKey: ['webhook-deliveries', projectKey, webhookId] }),
  })
}
