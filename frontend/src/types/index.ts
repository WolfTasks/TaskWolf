export type ProjectRole = 'ADMIN' | 'MEMBER' | 'VIEWER'
export type OrgRole = 'OWNER' | 'ADMIN' | 'MEMBER'

export interface User {
  id: string
  email: string
  displayName: string
  avatarUrl: string | null
  role: 'ADMIN' | 'MEMBER'
}

export interface Project {
  id: string
  key: string
  name: string
  description: string | null
  ownerId: string
  orgId: string | null
  archived: boolean
  myRole?: ProjectRole
}

export interface ProjectMember {
  user: User
  role: ProjectRole
}

export interface UserSearchResult {
  id: string
  email: string
  displayName: string
}

export interface Label {
  id: string
  name: string
  color: string
}

export interface Version {
  id: string
  name: string
}

export interface CustomFieldOption {
  id: string
  label: string
  sortOrder: number
}

export interface CustomFieldDefinition {
  id: string
  name: string
  type: 'TEXT' | 'NUMBER' | 'DATE' | 'DROPDOWN' | 'CHECKBOX'
  required: boolean
  sortOrder: number
  options?: CustomFieldOption[]
}

export interface CustomFieldValue {
  fieldId: string
  fieldName: string
  type: string
  required: boolean
  textValue?: string
  numberValue?: number
  dateValue?: string
  booleanValue?: boolean
  optionId?: string
  optionLabel?: string
}

export interface WorkflowStatus {
  id: string
  name: string
  category: 'TODO' | 'IN_PROGRESS' | 'DONE'
  color: string
  position: number
}

export interface IssueRefResponse {
  id: string
  provider: string
  refType: string
  externalId: string
  title: string | null
  url: string
  createdAt: string | null
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
  assigneeName: string | null
  reporterId: string
  reporterName: string
  parentId: string | null
  dueDate: string | null
  sprintId: string | null
  sprintName: string | null
  createdAt: string
  updatedAt: string
  refs?: IssueRefResponse[]
  labels?: Label[]
  fixVersions?: Version[]
  affectsVersions?: Version[]
  customFields?: CustomFieldValue[]
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
  type: 'COMMENT_MENTION' | 'ISSUE_ASSIGNED' | 'AUTOMATION'
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

export interface TransitionGuard {
  type: 'REQUIRED_FIELD' | 'ROLE_RESTRICTION'
  field?: string
  roles?: string[]
}

export interface WorkflowTransition {
  id: string
  fromStatusId: string | null
  toStatusId: string
  guards: string | null
}

export interface StatusPosition {
  statusId: string
  x: number
  y: number
}

export interface WorkflowEditorData {
  id: string
  name: string
  statuses: WorkflowStatus[]
  transitions: WorkflowTransition[]
  layout: StatusPosition[]
}

export type TriggerType =
  | 'ISSUE_CREATED' | 'STATUS_CHANGED' | 'PRIORITY_CHANGED'
  | 'ASSIGNEE_CHANGED' | 'COMMENT_ADDED' | 'SPRINT_STARTED' | 'SPRINT_COMPLETED'

export type ConditionType = 'ISSUE_TYPE' | 'PRIORITY' | 'ASSIGNEE' | 'STATUS' | 'STORY_POINTS' | 'PROJECT'
export type ActionType = 'SET_STATUS' | 'SET_ASSIGNEE' | 'SET_PRIORITY' | 'SEND_NOTIFICATION' | 'CREATE_COMMENT' | 'CREATE_SUBTASK'
export type GroupLogic = 'AND' | 'OR'

export interface RuleCondition {
  id?: string
  type: ConditionType
  operator: 'IS' | 'IS_NOT' | 'CONTAINS' | 'GT' | 'LT'
  params: Record<string, string>
}

export interface RuleConditionGroup {
  id?: string
  logic: GroupLogic
  conditions: RuleCondition[]
  childGroups: RuleConditionGroup[]
}

export interface RuleAction {
  id?: string
  position: number
  type: ActionType
  params: Record<string, string>
}

export interface AutomationRule {
  id: string
  name: string
  triggerType: TriggerType
  triggerPayload: string | null
  scope: 'PROJECT' | 'SYSTEM'
  enabled: boolean
  projectId: string | null
}

export interface AutomationRuleDraft {
  name: string
  triggerType: TriggerType
  triggerPayload?: string
  rootGroupLogic: GroupLogic
  conditions?: RuleCondition[]
  actions?: RuleAction[]
}
