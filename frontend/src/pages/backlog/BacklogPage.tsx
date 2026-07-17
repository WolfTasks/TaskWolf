import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router-dom'
import { useBacklog } from '@/hooks/useBoard'
import { useStartSprint, useAssignIssue, useUnassignIssue } from '@/hooks/useSprints'
import { useProjectSocket } from '@/hooks/useProjectSocket'
import { useProjectRole } from '@/hooks/useProjectRole'
import { useOpenIssue } from '@/hooks/useOpenIssue'
import { CreateSprintForm } from '@/components/sprint/CreateSprintForm'
import { StatusBadge } from '@/components/issue/StatusBadge'
import type { Issue } from '@/types'

function IssueRow({ issue, action, onOpen }: { issue: Issue; action: React.ReactNode; onOpen: (issueKey: string) => void }) {
  return (
    <div className="flex items-center gap-3 px-4 py-2.5 bg-gray-900/50 rounded border border-gray-800/50 hover:border-gray-700">
      <span className="text-xs text-gray-500 font-mono w-20 shrink-0">{issue.key}</span>
      <button
        onClick={() => onOpen(issue.key)}
        className="flex-1 text-left text-sm text-white truncate hover:text-blue-400"
      >
        {issue.title}
      </button>
      <StatusBadge name={issue.statusName} category={issue.statusCategory} />
      {issue.storyPoints != null && (
        <span className="text-xs bg-gray-800 text-gray-400 px-1.5 py-0.5 rounded font-mono">{issue.storyPoints}</span>
      )}
      {action}
    </div>
  )
}

export function BacklogPage() {
  const { t } = useTranslation('backlog')
  const { key } = useParams<{ key: string }>()
  useProjectSocket(key!)
  const { canWrite } = useProjectRole(key!)
  const { data: backlog, isLoading } = useBacklog(key!)
  const startSprint = useStartSprint(key!)
  const assignIssue = useAssignIssue(key!)
  const unassignIssue = useUnassignIssue(key!)
  const openIssue = useOpenIssue()
  const [showCreateForm, setShowCreateForm] = useState(false)
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set())

  const toggleCollapse = (id: string) =>
    setCollapsed(prev => { const s = new Set(prev); s.has(id) ? s.delete(id) : s.add(id); return s })

  if (isLoading) return <div className="text-gray-400">{t('common:loading')}</div>

  return (
    <div className="max-w-4xl">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">{t('title')}</h1>
        {canWrite && !showCreateForm && (
          <button onClick={() => setShowCreateForm(true)}
            className="text-sm bg-gray-800 hover:bg-gray-700 text-gray-300 px-3 py-1.5 rounded border border-gray-700">
            + {t('newSprint')}
          </button>
        )}
      </div>

      {showCreateForm && (
        <div className="mb-4">
          <CreateSprintForm
            projectKey={key!}
            onCreated={() => setShowCreateForm(false)}
            onCancel={() => setShowCreateForm(false)}
          />
        </div>
      )}

      {backlog?.sprints.map(entry => (
        <div key={entry.sprint.id} className="mb-4">
          <div className="flex items-center gap-3 mb-2">
            <button onClick={() => toggleCollapse(entry.sprint.id)} className="text-gray-400 hover:text-white text-xs">
              {collapsed.has(entry.sprint.id) ? '▶' : '▼'}
            </button>
            <span className="font-semibold text-white">{entry.sprint.name}</span>
            {entry.sprint.goal && <span className="text-xs text-gray-500">— {entry.sprint.goal}</span>}
            <span className="text-xs text-gray-500 ml-auto">{t('sprintMeta', { count: entry.issues.length, points: entry.totalPoints })}</span>
            {canWrite && (
              <button
                onClick={() => startSprint.mutate(entry.sprint.id)}
                disabled={startSprint.isPending}
                className="text-xs bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white px-3 py-1 rounded"
              >
                {t('startSprint')}
              </button>
            )}
          </div>
          {!collapsed.has(entry.sprint.id) && (
            <div className="flex flex-col gap-1 ml-5">
              {entry.issues.map(issue => (
                <IssueRow
                  key={issue.id}
                  issue={issue}
                  onOpen={openIssue}
                  action={
                    canWrite ? (
                      <button
                        onClick={() => unassignIssue.mutate({ sprintId: entry.sprint.id, issueId: issue.id })}
                        className="text-xs text-gray-500 hover:text-red-400 shrink-0"
                      >
                        ✕
                      </button>
                    ) : null
                  }
                />
              ))}
              {entry.issues.length === 0 && (
                <p className="text-xs text-gray-600 py-2 ml-1">{t('emptySprint')}</p>
              )}
            </div>
          )}
        </div>
      ))}

      <div className="mt-6">
        <div className="flex items-center gap-3 mb-2">
          <span className="font-semibold text-gray-300">{t('title')}</span>
          <span className="text-xs text-gray-500">{t('issueCount', { count: backlog?.backlogIssues.length ?? 0 })}</span>
        </div>
        <div className="flex flex-col gap-1">
          {backlog?.backlogIssues.map(issue => (
            <IssueRow
              key={issue.id}
              issue={issue}
              onOpen={openIssue}
              action={
                canWrite && backlog.sprints.length > 0 ? (
                  <select
                    onChange={e => e.target.value && assignIssue.mutate({ sprintId: e.target.value, issueId: issue.id })}
                    defaultValue=""
                    className="text-xs bg-gray-800 text-gray-400 border border-gray-700 rounded px-2 py-1 shrink-0"
                  >
                    <option value="" disabled>{t('addToSprint')}</option>
                    {backlog.sprints.map(s => (
                      <option key={s.sprint.id} value={s.sprint.id}>{s.sprint.name}</option>
                    ))}
                  </select>
                ) : null
              }
            />
          ))}
          {backlog?.backlogIssues.length === 0 && (
            <p className="text-sm text-gray-600 py-4 text-center">{t('emptyBacklog')}</p>
          )}
        </div>
      </div>
    </div>
  )
}
