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

export interface Sprint {
  id: string
  name: string
  goal: string | null
  status: 'PLANNED' | 'ACTIVE' | 'CLOSED'
  startDate: string | null
  endDate: string | null
  plannedPoints: number | null
  completedPoints: number | null
  projectId: string
}

export interface BoardSprintSummary {
  id: string
  name: string
  goal: string | null
  startDate: string | null
  endDate: string | null
  daysRemaining: number | null
  totalPoints: number | null
  completedPoints: number
}

export interface BoardColumn {
  status: { id: string; name: string; category: string; color: string }
  issues: Issue[]
}

export interface BoardResponse {
  sprint: BoardSprintSummary
  columns: BoardColumn[]
}

export interface BacklogSprintEntry {
  sprint: Sprint
  issues: Issue[]
  totalPoints: number
}

export interface BacklogResponse {
  sprints: BacklogSprintEntry[]
  backlogIssues: Issue[]
}

export interface BurndownDay {
  date: string
  idealPoints: number
  remainingPoints: number
}

export interface BurndownResponse {
  sprintId: string
  days: BurndownDay[]
}

export interface VelocityEntry {
  sprintId: string
  sprintName: string
  plannedPoints: number
  completedPoints: number
}

export interface VelocityResponse {
  entries: VelocityEntry[]
}

export interface Comment {
  id: string
  issueId: string
  authorId: string
  body: string | null
  editedAt: string | null
  deleted: boolean
  createdAt: string
}

export type ActivityType =
  | 'COMMENT'
  | 'STATUS_CHANGED'
  | 'ASSIGNED'
  | 'UNASSIGNED'
  | 'PRIORITY_CHANGED'
  | 'TITLE_CHANGED'
  | 'DESCRIPTION_CHANGED'
  | 'STORY_POINTS_CHANGED'
  | 'DUE_DATE_CHANGED'
  | 'SPRINT_CHANGED'
  | 'ATTACHMENT_ADDED'
  | 'ATTACHMENT_REMOVED'

export interface ActivityItem {
  id: string
  issueId: string
  actorId: string
  type: ActivityType
  commentId: string | null
  oldValue: string | null
  newValue: string | null
  createdAt: string
}

export interface Notification {
  id: string
  type: 'COMMENT_MENTION' | 'ISSUE_ASSIGNED'
  title: string
  body: string | null
  link: string | null
  read: boolean
  createdAt: string
}

export interface Attachment {
  id: string
  issueId: string
  uploaderId: string
  filename: string
  contentType: string
  size: number
  createdAt: string
}
