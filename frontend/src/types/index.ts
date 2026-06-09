export interface User {
  id: string
  email: string
  displayName: string
  avatarUrl: string | null
}

export interface Project {
  id: string
  key: string
  name: string
  description: string | null
  ownerId: string
  archived: boolean
}

export interface WorkflowStatus {
  id: string
  name: string
  category: 'TODO' | 'IN_PROGRESS' | 'DONE'
  color: string
  position: number
}

export interface Issue {
  id: string
  key: string
  title: string
  description: string | null
  type: 'EPIC' | 'STORY' | 'BUG' | 'TASK' | 'SUBTASK'
  priority: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
  storyPoints: number | null
  statusId: string
  statusName: string
  statusCategory: 'TODO' | 'IN_PROGRESS' | 'DONE'
  projectId: string
  assigneeId: string | null
  reporterId: string
  parentId: string | null
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
}
