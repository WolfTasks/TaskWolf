import React from 'react'
import ReactGridLayout, { WidthProvider } from 'react-grid-layout/legacy'
import 'react-grid-layout/css/styles.css'
import 'react-resizable/css/styles.css'
import { WidgetData } from '@/hooks/useProjectDashboard'

const ResponsiveGrid = WidthProvider(ReactGridLayout)

interface Props {
  widgets: WidgetData[]
  editMode: boolean
  onLayoutChange: (layout: Array<{ widgetId: string; gridX: number; gridY: number; gridW: number; gridH: number }>) => void
  renderWidget: (widget: WidgetData) => React.ReactNode
}

export function DashboardCanvas({ widgets, editMode, onLayoutChange, renderWidget }: Props) {
  const layout = widgets.map(w => ({
    i: w.id,
    x: w.gridX,
    y: w.gridY,
    w: w.gridW,
    h: w.gridH,
  }))

  const handleLayoutChange = (newLayout: readonly any[]) => {
    const items = newLayout.map((item: any) => ({
      widgetId: item.i,
      gridX: item.x,
      gridY: item.y,
      gridW: item.w,
      gridH: item.h,
    }))
    onLayoutChange(items)
  }

  return (
    <ResponsiveGrid
      className="layout"
      layout={layout}
      cols={12}
      rowHeight={60}
      isDraggable={editMode}
      isResizable={editMode}
      onLayoutChange={handleLayoutChange}
      draggableHandle=".drag-handle"
    >
      {widgets.map(w => (
        <div key={w.id}>
          {renderWidget(w)}
        </div>
      ))}
    </ResponsiveGrid>
  )
}
