import React from 'react'
import { useTranslation } from 'react-i18next'

interface Props {
  title: string
  widgetId: string
  editMode: boolean
  onRemove: (id: string) => void
  children: React.ReactNode
}

interface State { hasError: boolean }

class ErrorBoundary extends React.Component<{ children: React.ReactNode; message: string }, State> {
  state: State = { hasError: false }
  static getDerivedStateFromError() { return { hasError: true } }
  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error('Widget render error:', error, info.componentStack)
  }
  render() {
    if (this.state.hasError)
      return <div className="flex items-center justify-center h-full text-red-400 text-sm">{this.props.message}</div>
    return this.props.children
  }
}

export function WidgetWrapper({ title, widgetId, editMode, onRemove, children }: Props) {
  const { t } = useTranslation('dashboard')
  return (
    <div className="bg-gray-900 border border-gray-800 rounded-lg flex flex-col h-full overflow-hidden">
      <div className={`flex items-center justify-between px-4 py-2 border-b border-gray-800 shrink-0 ${editMode ? 'drag-handle cursor-grab active:cursor-grabbing' : ''}`}>
        <span className="text-sm font-semibold text-gray-300">{title}</span>
        {editMode && (
          <button
            onClick={() => onRemove(widgetId)}
            aria-label={t('widget.removeAria', { title })}
            className="text-gray-500 hover:text-red-400 text-xs ml-2"
          >
            ✕
          </button>
        )}
      </div>
      <div className="flex-1 min-h-0 p-3">
        <ErrorBoundary message={t('error.widgetFailed')}>{children}</ErrorBoundary>
      </div>
    </div>
  )
}
