import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useIssue, useUpdateIssue } from '@/hooks/useIssues'
import { useMe } from '@/hooks/useAuth'
import { useProjectRole } from '@/hooks/useProjectRole'
import { useSprints } from '@/hooks/useSprints'
import { useProjectMembers } from '@/hooks/useProjectMembers'
import { useLabels } from '@/hooks/useLabels'
import { useVersions } from '@/hooks/useVersions'
import { useCustomFields } from '@/hooks/useCustomFields'
import { CustomFieldInput } from '@/components/issue/CustomFieldInput'
import { StatusBadge } from '@/components/issue/StatusBadge'
import { InlineEditTitle } from '@/components/issue/InlineEditTitle'
import { PrioritySelector } from '@/components/issue/PrioritySelector'
import { TypeSelector } from '@/components/issue/TypeSelector'
import { AssigneeSelector } from '@/components/issue/AssigneeSelector'
import { SprintSelector } from '@/components/issue/SprintSelector'
import { DueDatePicker } from '@/components/issue/DueDatePicker'
import { LabelSelector } from '@/components/issue/LabelSelector'
import { VersionSelector } from '@/components/issue/VersionSelector'
import { StoryPointsSelector } from '@/components/issue/StoryPointsSelector'
import { RichTextEditor } from '@/components/issue/RichTextEditor'
import { CommentsActivityTabs } from '@/components/comments/CommentsActivityTabs'
import { AttachmentPanel } from '@/components/attachments/AttachmentPanel'

function SidebarField({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="text-xs text-gray-500 uppercase tracking-wider mb-1 block">{label}</label>
      {children}
    </div>
  )
}

interface Props { projectKey: string; issueKey: string }

export function IssueDetailContent({ projectKey, issueKey }: Props) {
  const { t } = useTranslation('issues')
  const navigate = useNavigate()
  const { data: issue, isLoading } = useIssue(projectKey, issueKey)
  const { data: me } = useMe()
  const { canWrite } = useProjectRole(projectKey)
  const updateIssue = useUpdateIssue(projectKey)
  const { data: members = [] } = useProjectMembers(projectKey)
  const { data: sprints = [] } = useSprints(projectKey)
  const { data: allLabels = [] } = useLabels(projectKey)
  const { data: allVersions = [] } = useVersions(projectKey)
  const { data: customFieldDefs = [] } = useCustomFields(projectKey)

  if (isLoading) return <div className="text-gray-400">{t('common:loading')}</div>
  if (!issue) return <div className="text-red-400">{t('detail.notFound')}</div>

  function patch(data: Record<string, unknown>) {
    if (!canWrite) return
    updateIssue.mutate({ id: issue!.id, data })
  }

  return (
    <div>
      {/* Header */}
      <div className="flex items-center gap-3 mb-2">
        <span className="text-sm text-gray-500 font-mono">{issue.key}</span>
        <StatusBadge name={issue.statusName} category={issue.statusCategory} />
      </div>

      <InlineEditTitle value={issue.title} onSave={title => patch({ title })} disabled={!canWrite} />

      {/* Two-column layout */}
      <div className="flex flex-col lg:flex-row gap-8">
        {/* Left: description + comments + activity */}
        <div className="flex-1 min-w-0 space-y-8">
          <section>
            <h2 className="text-sm font-medium text-gray-400 mb-2">{t('detail.description')}</h2>
            <RichTextEditor
              value={issue.description}
              onSave={description => patch({ description })}
              disabled={!canWrite}
            />
          </section>

          <section>
            <CommentsActivityTabs projectKey={projectKey} issueKey={issueKey} currentUserId={me?.id} readOnly={!canWrite} />
          </section>

          {issue.refs && issue.refs.length > 0 && (
            <div className="mt-6">
              <h3 className="text-sm font-semibold text-gray-400 uppercase tracking-wider mb-3">{t('detail.references')}</h3>
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
        <div className="w-full lg:w-80 shrink-0 flex flex-col gap-4">
          <section className="space-y-4">
            <SidebarField label={t('detail.field.priority')}>
              <PrioritySelector value={issue.priority} onSave={priority => patch({ priority })} disabled={!canWrite} />
            </SidebarField>

            <SidebarField label={t('detail.field.type')}>
              <TypeSelector value={issue.type} onSave={type => patch({ type })} disabled={!canWrite} />
            </SidebarField>

            <SidebarField label={t('detail.field.assignee')}>
              <AssigneeSelector
                value={issue.assigneeName}
                assigneeId={issue.assigneeId}
                members={members.map(m => m.user)}
                onSave={userId => userId ? patch({ assigneeId: userId }) : patch({ clearAssignee: true })}
                disabled={!canWrite}
              />
            </SidebarField>

            <SidebarField label={t('detail.field.reporter')}>
              <span className="text-sm text-gray-300">{issue.reporterName}</span>
            </SidebarField>

            <SidebarField label={t('detail.field.sprint')}>
              <SprintSelector
                value={issue.sprintName}
                sprintId={issue.sprintId}
                sprints={sprints}
                onSave={sprintId => sprintId ? patch({ sprintId }) : patch({ clearSprint: true })}
                disabled={!canWrite}
              />
            </SidebarField>

            <SidebarField label={t('detail.field.dueDate')}>
              <DueDatePicker
                value={issue.dueDate}
                onSave={date => date ? patch({ dueDate: date }) : patch({ clearDueDate: true })}
                disabled={!canWrite}
              />
            </SidebarField>

            <SidebarField label={t('detail.field.labels')}>
              <LabelSelector
                projectKey={projectKey}
                value={issue.labels ?? []}
                allLabels={allLabels}
                onSave={labelIds => patch({ labelIds })}
                onChipClick={l => navigate(`/p/${projectKey}/issues?labelId=${l.id}`)}
                disabled={!canWrite}
              />
            </SidebarField>

            <SidebarField label={t('detail.field.fixVersions')}>
              <VersionSelector
                value={issue.fixVersions ?? []}
                allVersions={allVersions}
                onSave={fixVersionIds => patch({ fixVersionIds })}
                onChipClick={v => navigate(`/p/${projectKey}/issues?fixVersionId=${v.id}`)}
                disabled={!canWrite}
              />
            </SidebarField>

            <SidebarField label={t('detail.field.affectsVersions')}>
              <VersionSelector
                value={issue.affectsVersions ?? []}
                allVersions={allVersions}
                onSave={affectsVersionIds => patch({ affectsVersionIds })}
                onChipClick={v => navigate(`/p/${projectKey}/issues?affectsVersionId=${v.id}`)}
                disabled={!canWrite}
              />
            </SidebarField>

            {customFieldDefs.map(def => {
              const cfValue = issue.customFields?.find(cf => cf.fieldId === def.id)
              return (
                <SidebarField key={def.id} label={def.required ? `${def.name} *` : def.name}>
                  <CustomFieldInput
                    definition={def}
                    value={cfValue}
                    onChange={val => patch({ customFieldValues: [{ fieldId: def.id, value: val }] })}
                    disabled={!canWrite}
                  />
                </SidebarField>
              )
            })}

            <SidebarField label={t('detail.field.storyPoints')}>
              <StoryPointsSelector
                value={issue.storyPoints}
                onSave={sp => sp != null ? patch({ storyPoints: sp }) : patch({ clearStoryPoints: true })}
                disabled={!canWrite}
              />
            </SidebarField>

            <SidebarField label={t('detail.field.created')}>
              <span className="text-xs text-gray-500">{new Date(issue.createdAt).toLocaleDateString()}</span>
            </SidebarField>

            <SidebarField label={t('detail.field.updated')}>
              <span className="text-xs text-gray-500">{new Date(issue.updatedAt).toLocaleDateString()}</span>
            </SidebarField>
          </section>

          <section>
            <AttachmentPanel projectKey={projectKey} issueKey={issueKey} currentUserId={me?.id} readOnly={!canWrite} />
          </section>
        </div>
      </div>
    </div>
  )
}
