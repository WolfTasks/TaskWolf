import { useParams } from 'react-router-dom'
import { useIssue, useUpdateIssue } from '@/hooks/useIssues'
import { useMe } from '@/hooks/useAuth'
import { useSprints } from '@/hooks/useSprints'
import { useProjectMembers } from '@/hooks/useProjectMembers'
import { StatusBadge } from '@/components/issue/StatusBadge'
import { InlineEditTitle } from '@/components/issue/InlineEditTitle'
import { PrioritySelector } from '@/components/issue/PrioritySelector'
import { TypeSelector } from '@/components/issue/TypeSelector'
import { AssigneeSelector } from '@/components/issue/AssigneeSelector'
import { SprintSelector } from '@/components/issue/SprintSelector'
import { DueDatePicker } from '@/components/issue/DueDatePicker'
import { CommentThread } from '@/components/comments/CommentThread'
import { ActivityFeed } from '@/components/comments/ActivityFeed'
import { AttachmentPanel } from '@/components/attachments/AttachmentPanel'

function SidebarField({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="text-xs text-gray-500 uppercase tracking-wider mb-1 block">{label}</label>
      {children}
    </div>
  )
}

export function IssueDetailPage() {
  const { key, issueKey } = useParams<{ key: string; issueKey: string }>()
  const { data: issue, isLoading } = useIssue(key!, issueKey!)
  const { data: me } = useMe()
  const updateIssue = useUpdateIssue(key!)
  const { data: members = [] } = useProjectMembers(key!)
  const { data: sprints = [] } = useSprints(key!)

  if (isLoading) return <div className="text-gray-400">Loading...</div>
  if (!issue) return <div className="text-red-400">Issue not found</div>

  function patch(data: Record<string, unknown>) {
    updateIssue.mutate({ id: issue!.id, data })
  }

  return (
    <div className="max-w-5xl">
      {/* Header */}
      <div className="flex items-center gap-3 mb-2">
        <span className="text-sm text-gray-500 font-mono">{issue.key}</span>
        <StatusBadge name={issue.statusName} category={issue.statusCategory} />
      </div>

      <InlineEditTitle value={issue.title} onSave={title => patch({ title })} />

      {/* Two-column layout */}
      <div className="grid grid-cols-3 gap-8">
        {/* Left: description + comments + activity */}
        <div className="col-span-2 space-y-8">
          {/* Description (plain display — replaced in Task 4) */}
          <section>
            <h2 className="text-sm font-medium text-gray-400 mb-2">Description</h2>
            <div className="bg-gray-900 rounded-lg p-4 text-sm text-gray-300 min-h-24">
              {issue.description
                ? <div dangerouslySetInnerHTML={{ __html: issue.description }} />
                : <span className="text-gray-600 italic">No description</span>}
            </div>
          </section>

          <section>
            <CommentThread projectKey={key!} issueKey={issueKey!} currentUserId={me?.id} />
          </section>

          <section>
            <ActivityFeed projectKey={key!} issueKey={issueKey!} />
          </section>

          {issue.refs && issue.refs.length > 0 && (
            <div className="mt-6">
              <h3 className="text-sm font-semibold text-gray-400 uppercase tracking-wider mb-3">References</h3>
              <div className="space-y-2">
                {issue.refs.map((ref) => (
                  <a key={ref.id} href={ref.url} target="_blank" rel="noopener noreferrer"
                    className="flex items-center gap-3 p-3 bg-gray-800 rounded hover:bg-gray-700 transition-colors">
                    <span className="text-xs font-bold px-2 py-0.5 rounded bg-gray-700 text-gray-300">{ref.provider}</span>
                    <span className="text-xs px-2 py-0.5 rounded bg-gray-700 text-gray-400">{ref.refType}</span>
                    <span className="text-sm text-blue-400 truncate">{ref.title || ref.externalId}</span>
                    <span className="text-xs text-gray-500 shrink-0">
                      {ref.createdAt ? new Date(ref.createdAt).toLocaleDateString() : ''}
                    </span>
                  </a>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Right: metadata + attachments */}
        <div className="flex flex-col gap-4">
          <section className="space-y-4">
            <SidebarField label="Priority">
              <PrioritySelector
                value={issue.priority}
                onSave={priority => patch({ priority })}
              />
            </SidebarField>

            <SidebarField label="Type">
              <TypeSelector
                value={issue.type}
                onSave={type => patch({ type })}
              />
            </SidebarField>

            <SidebarField label="Assignee">
              <AssigneeSelector
                value={issue.assigneeName}
                assigneeId={issue.assigneeId}
                members={members}
                onSave={userId =>
                  userId ? patch({ assigneeId: userId }) : patch({ clearAssignee: true })
                }
              />
            </SidebarField>

            <SidebarField label="Reporter">
              <span className="text-sm text-gray-300">{issue.reporterName}</span>
            </SidebarField>

            <SidebarField label="Sprint">
              <SprintSelector
                value={issue.sprintName}
                sprintId={issue.sprintId}
                sprints={sprints}
                onSave={sprintId =>
                  sprintId ? patch({ sprintId }) : patch({ clearSprint: true })
                }
              />
            </SidebarField>

            <SidebarField label="Due Date">
              <DueDatePicker
                value={issue.dueDate}
                onSave={date =>
                  date ? patch({ dueDate: date }) : patch({ clearDueDate: true })
                }
              />
            </SidebarField>

            {issue.storyPoints != null && (
              <SidebarField label="Story Points">
                <span className="text-sm text-white">{issue.storyPoints}</span>
              </SidebarField>
            )}

            <SidebarField label="Created">
              <span className="text-xs text-gray-500">{new Date(issue.createdAt).toLocaleDateString()}</span>
            </SidebarField>

            <SidebarField label="Updated">
              <span className="text-xs text-gray-500">{new Date(issue.updatedAt).toLocaleDateString()}</span>
            </SidebarField>
          </section>

          <section>
            <AttachmentPanel projectKey={key!} issueKey={issueKey!} currentUserId={me?.id} />
          </section>
        </div>
      </div>
    </div>
  )
}
