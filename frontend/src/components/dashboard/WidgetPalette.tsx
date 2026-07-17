import { useState } from 'react'
import { useTranslation } from 'react-i18next'

const WIDGET_OPTIONS = [
  { type: 'BURNDOWN',         defaultW: 6, defaultH: 5 },
  { type: 'VELOCITY',         defaultW: 6, defaultH: 5 },
  { type: 'CYCLE_TIME',       defaultW: 6, defaultH: 5 },
  { type: 'ISSUE_COUNT',      defaultW: 3, defaultH: 3 },
  { type: 'ISSUES_BY_STATUS', defaultW: 6, defaultH: 3 },
  { type: 'ISSUE_LIST',       defaultW: 4, defaultH: 6 },
]

const ISSUE_LIST_FILTERS = ['MY_OPEN', 'RECENTLY_UPDATED', 'OVERDUE']

interface Props {
  onAdd: (type: string, config: string | undefined, w: number, h: number) => void
  onClose: () => void
}

export function WidgetPalette({ onAdd, onClose }: Props) {
  const { t } = useTranslation('dashboard')
  const [issueFilter, setIssueFilter] = useState('MY_OPEN')

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50" onClick={onClose}>
      <div
        className="bg-gray-900 border border-gray-700 rounded-lg p-6 w-96 shadow-xl"
        onClick={e => e.stopPropagation()}
      >
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold">{t('palette.title')}</h2>
          <button onClick={onClose} aria-label={t('common:close')} className="text-gray-500 hover:text-white">✕</button>
        </div>
        <div className="flex flex-col gap-2">
          {WIDGET_OPTIONS.map(opt => (
            <div key={opt.type} className="flex flex-col gap-1">
              {opt.type === 'ISSUE_LIST' && (
                <select
                  value={issueFilter}
                  onChange={e => setIssueFilter(e.target.value)}
                  className="bg-gray-800 border border-gray-700 text-xs text-white rounded px-2 py-1"
                >
                  {ISSUE_LIST_FILTERS.map(f => (
                    <option key={f} value={f}>{t(`palette.filters.${f}`)}</option>
                  ))}
                </select>
              )}
              <button
                onClick={() => {
                  const config = opt.type === 'ISSUE_LIST'
                    ? JSON.stringify({ filter: issueFilter })
                    : undefined
                  onAdd(opt.type, config, opt.defaultW, opt.defaultH)
                  onClose()
                }}
                className="w-full text-left px-4 py-2 rounded bg-gray-800 hover:bg-gray-700 text-sm text-white"
              >
                {t(`palette.options.${opt.type}`)}
              </button>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
