import { useEffect } from 'react'
import { Client } from '@stomp/stompjs'
import { useQueryClient } from '@tanstack/react-query'

export function useProjectSocket(projectKey: string) {
  const queryClient = useQueryClient()

  useEffect(() => {
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const client = new Client({
      brokerURL: `${protocol}://${window.location.host}/ws-stomp`,
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe(`/topic/projects/${projectKey}`, (message) => {
          const event = JSON.parse(message.body) as { type: string }
          if (event.type === 'ISSUE_MOVED') {
            queryClient.invalidateQueries({ queryKey: ['board', projectKey] })
          } else if (event.type === 'SPRINT_UPDATED') {
            queryClient.invalidateQueries({ queryKey: ['board', projectKey] })
            queryClient.invalidateQueries({ queryKey: ['sprints', projectKey] })
            queryClient.invalidateQueries({ queryKey: ['backlog', projectKey] })
          }
        })
      },
    })
    client.activate()
    return () => { client.deactivate() }
  }, [projectKey, queryClient])
}
