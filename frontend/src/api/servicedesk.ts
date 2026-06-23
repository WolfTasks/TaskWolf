import { apiClient } from './client'

export const serviceDeskApi = {
  get: (key: string) => apiClient.get(`/projects/${key}/service-desk`).then(r => r.data),
  enable: (key: string, emailAddress?: string) =>
    apiClient.post(`/projects/${key}/service-desk/enable`, { emailAddress }).then(r => r.data),
  listTickets: (key: string) => apiClient.get(`/projects/${key}/service-desk/tickets`).then(r => r.data),
  submitTicket: (key: string, title: string, description: string) =>
    apiClient.post(`/projects/${key}/service-desk/tickets`, { title, description }),
  listSlaPolicies: (key: string) => apiClient.get(`/projects/${key}/service-desk/sla-policies`).then(r => r.data),
  listIncidents: (key: string) => apiClient.get(`/projects/${key}/incidents`).then(r => r.data),
  resolveIncident: (key: string, id: string, postmortemBody?: string) =>
    apiClient.patch(`/projects/${key}/incidents/${id}`, { postmortemBody }),
}
