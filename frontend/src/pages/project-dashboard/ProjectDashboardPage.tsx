import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useProjectDashboard, useSaveDashboardLayout, useAddWidget, useRemoveWidget } from '@/hooks/useProjectDashboard'
import type { WidgetData } from '@/hooks/useProjectDashboard'
import { DashboardCanvas } from '@/components/dashboard/DashboardCanvas'
import { WidgetWrapper } from '@/components/dashboard/WidgetWrapper'
import { WidgetPalette } from '@/components/dashboard/WidgetPalette'
import { BurndownWidget } from '@/components/dashboard/BurndownWidget'
import { VelocityWidget } from '@/components/dashboard/VelocityWidget'
import { CycleTimeWidget } from '@/components/dashboard/CycleTimeWidget'
import { IssueCountWidget } from '@/components/dashboard/IssueCountWidget'
import { IssuesByStatusWidget } from '@/components/dashboard/IssuesByStatusWidget'
import { IssueListWidget } from '@/components/dashboard/IssueListWidget'

type LayoutItem = { widgetId: string; gridX: number; gridY: number; gridW: number; gridH: number }

const WIDGET_TITLES: Record<string, string> = {
  BURNDOWN:         'Burndown',
  VELOCITY:         'Velocity',
  CYCLE_TIME:       'Cycle Time',
  ISSUE_COUNT:      'Open Issues',
  ISSUES_BY_STATUS: 'Issues by Status',
  ISSUE_LIST:       'Issue List',
}

export function ProjectDashboardPage() {
  const { key } = useParams<{ key: string }>()
  const projectKey = key!
  const [editMode, setEditMode] = useState(false)
  const [showPalette, setShowPalette] = useState(false)
  const [pendingLayout, setPendingLayout] = useState<LayoutItem[] | null>(null)
  const [mutationError, setMutationError] = useState<string | null>(null)

  const { data: dashboard, isLoading } = useProjectDashboard(projectKey)
  const saveLayout = useSaveDashboardLayout(projectKey)
  const addWidget = useAddWidget(projectKey)
  const removeWidget = useRemoveWidget(projectKey)

  function renderWidget(widget: WidgetData) {
    const inner = (() => {
      switch (widget.type) {
        case 'BURNDOWN':         return <BurndownWidget projectKey={projectKey} config={widget.config} />
        case 'VELOCITY':         return <VelocityWidget projectKey={projectKey} />
        case 'CYCLE_TIME':       return <CycleTimeWidget projectKey={projectKey} />
        case 'ISSUE_COUNT':      return <IssueCountWidget projectKey={projectKey} />
        case 'ISSUES_BY_STATUS': return <IssuesByStatusWidget projectKey={projectKey} />
        case 'ISSUE_LIST':       return <IssueListWidget projectKey={projectKey} config={widget.config} />
        default:                 return <p className="text-gray-500 text-xs">Unknown widget type: {widget.type}</p>
      }
    })()

    return (
      <WidgetWrapper
        title={WIDGET_TITLES[widget.type] ?? widget.type}
        widgetId={widget.id}
        editMode={editMode}
        onRemove={id => removeWidget.mutate(id, { onError: () => setMutationError('Admin role required to modify the dashboard.') })}
      >
        {inner}
      </WidgetWrapper>
    )
  }

  function handleSave() {
    if (!pendingLayout) { setEditMode(false); return }
    const items = pendingLayout.map(l => ({
      widgetId: l.widgetId,
      gridX: l.gridX,
      gridY: l.gridY,
      gridW: l.gridW,
      gridH: l.gridH,
    }))
    saveLayout.mutate(items, {
      onSuccess: () => { setEditMode(false); setPendingLayout(null); setMutationError(null) },
      onError: () => setMutationError('Admin role required to modify the dashboard.'),
    })
  }

  if (isLoading) return <p className="text-gray-500 text-sm">Loading dashboard...</p>

  return (
    <div className="max-w-7xl">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Dashboard</h1>
        <div className="flex gap-2">
          {editMode && (
            <>
              <button
                onClick={() => setShowPalette(true)}
                className="px-3 py-1.5 text-sm bg-gray-700 hover:bg-gray-600 text-white rounded"
              >
                + Add Widget
              </button>
              <button
                onClick={handleSave}
                className="px-3 py-1.5 text-sm bg-indigo-600 hover:bg-indigo-500 text-white rounded"
              >
                {saveLayout.isPending ? 'Saving...' : 'Save'}
              </button>
              <button
                onClick={() => { setEditMode(false); setPendingLayout(null); setMutationError(null) }}
                className="px-3 py-1.5 text-sm text-gray-400 hover:text-white"
              >
                Cancel
              </button>
            </>
          )}
          {!editMode && (
            <button
              onClick={() => setEditMode(true)}
              className="px-3 py-1.5 text-sm bg-gray-700 hover:bg-gray-600 text-white rounded"
            >
              Edit
            </button>
          )}
        </div>
      </div>

      {mutationError && (
        <div className="flex items-center justify-between bg-red-900/40 border border-red-700 text-red-300 text-sm px-4 py-2 rounded mb-4">
          <span>{mutationError}</span>
          <button onClick={() => setMutationError(null)} className="ml-4 text-red-400 hover:text-white">✕</button>
        </div>
      )}

      {dashboard?.widgets.length === 0 && !editMode && (
        <div className="flex flex-col items-center justify-center py-20 text-center">
          <p className="text-gray-400 mb-3">No widgets yet.</p>
          <button
            onClick={() => setEditMode(true)}
            className="px-4 py-2 text-sm bg-indigo-600 hover:bg-indigo-500 text-white rounded"
          >
            Add your first widget
          </button>
        </div>
      )}

      {(dashboard?.widgets.length ?? 0) > 0 && (
        <DashboardCanvas
          widgets={dashboard?.widgets ?? []}
          editMode={editMode}
          onLayoutChange={layout => setPendingLayout([...layout])}
          renderWidget={renderWidget}
        />
      )}

      {showPalette && (
        <WidgetPalette
          onAdd={(type, config, w, h) => addWidget.mutate(
            { type, config, gridX: 0, gridY: 9999, gridW: w, gridH: h },
            { onError: () => setMutationError('Admin role required to modify the dashboard.') }
          )}
          onClose={() => setShowPalette(false)}
        />
      )}
    </div>
  )
}
