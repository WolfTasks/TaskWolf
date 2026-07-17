import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useProjectDashboard, useSaveDashboardLayout, useAddWidget, useRemoveWidget } from '@/hooks/useProjectDashboard'
import type { WidgetData } from '@/hooks/useProjectDashboard'
import { DashboardCanvas } from '@/components/dashboard/DashboardCanvas'
import { WidgetWrapper } from '@/components/dashboard/WidgetWrapper'
import { WidgetPalette } from '@/components/dashboard/WidgetPalette'
import { useTranslation } from 'react-i18next'
import { BurndownWidget } from '@/components/dashboard/BurndownWidget'
import { VelocityWidget } from '@/components/dashboard/VelocityWidget'
import { CycleTimeWidget } from '@/components/dashboard/CycleTimeWidget'
import { IssueCountWidget } from '@/components/dashboard/IssueCountWidget'
import { IssuesByStatusWidget } from '@/components/dashboard/IssuesByStatusWidget'
import { IssueListWidget } from '@/components/dashboard/IssueListWidget'

type LayoutItem = { widgetId: string; gridX: number; gridY: number; gridW: number; gridH: number }

export function ProjectDashboardPage() {
  const { t } = useTranslation('dashboard')
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
        default:                 return <p className="text-gray-500 text-xs">{t('widget.unknown', { type: widget.type })}</p>
      }
    })()

    return (
      <WidgetWrapper
        title={t(`widget.title.${widget.type}`, { defaultValue: widget.type })}
        widgetId={widget.id}
        editMode={editMode}
        onRemove={id => removeWidget.mutate(id, { onError: () => setMutationError(t('error.adminRequired')) })}
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
      onError: () => setMutationError(t('error.adminRequired')),
    })
  }

  if (isLoading) return <p className="text-gray-500 text-sm">{t('loading')}</p>

  return (
    <div className="max-w-7xl">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">{t('title')}</h1>
        <div className="flex gap-2">
          {editMode && (
            <>
              <button
                onClick={() => setShowPalette(true)}
                className="px-3 py-1.5 text-sm bg-gray-700 hover:bg-gray-600 text-white rounded"
              >
                + {t('actions.addWidget')}
              </button>
              <button
                onClick={handleSave}
                className="px-3 py-1.5 text-sm bg-indigo-600 hover:bg-indigo-500 text-white rounded"
              >
                {saveLayout.isPending ? t('common:saving') : t('common:save')}
              </button>
              <button
                onClick={() => { setEditMode(false); setPendingLayout(null); setMutationError(null) }}
                className="px-3 py-1.5 text-sm text-gray-400 hover:text-white"
              >
                {t('common:cancel')}
              </button>
            </>
          )}
          {!editMode && (
            <button
              onClick={() => setEditMode(true)}
              className="px-3 py-1.5 text-sm bg-gray-700 hover:bg-gray-600 text-white rounded"
            >
              {t('actions.edit')}
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
          <p className="text-gray-400 mb-3">{t('empty.text')}</p>
          <button
            onClick={() => setEditMode(true)}
            className="px-4 py-2 text-sm bg-indigo-600 hover:bg-indigo-500 text-white rounded"
          >
            {t('empty.cta')}
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
            { onError: () => setMutationError(t('error.adminRequired')) }
          )}
          onClose={() => setShowPalette(false)}
        />
      )}
    </div>
  )
}
