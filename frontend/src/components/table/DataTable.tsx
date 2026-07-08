import { useRef } from 'react'
import type { ReactNode } from 'react'
import { useVirtualizer } from '@tanstack/react-virtual'

export interface Column<T> {
  key: string
  header: ReactNode
  cell: (row: T) => ReactNode
  width?: string // CSS-Grid-Track, z.B. '1fr' | '180px'. Default '1fr'.
  align?: 'left' | 'right' | 'center'
}

export interface DataTableProps<T> {
  columns: Column<T>[]
  rows: T[]
  rowKey: (row: T) => string | number
  empty?: ReactNode
  estimateRowHeight?: number
}

const ALIGN: Record<'left' | 'right' | 'center', string> = {
  left: 'text-left',
  right: 'text-right',
  center: 'text-center',
}

export function DataTable<T>({
  columns,
  rows,
  rowKey,
  empty = 'Keine Einträge',
  estimateRowHeight = 44,
}: DataTableProps<T>) {
  const parentRef = useRef<HTMLDivElement>(null)
  const gridTemplate = columns.map(c => c.width ?? '1fr').join(' ')

  const virtualizer = useVirtualizer({
    count: rows.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => estimateRowHeight,
    overscan: 10,
  })

  return (
    <div ref={parentRef} className="flex-1 min-h-0 overflow-auto">
      <div
        className="sticky top-0 z-10 grid bg-gray-900 text-gray-400 border-b border-gray-700"
        style={{ gridTemplateColumns: gridTemplate }}
      >
        {columns.map(col => (
          <div
            key={col.key}
            className={`px-2 py-2 text-sm font-medium ${ALIGN[col.align ?? 'left']}`}
          >
            {col.header}
          </div>
        ))}
      </div>

      {rows.length === 0 ? (
        <div className="py-4 text-sm text-gray-400">{empty}</div>
      ) : (
        <div style={{ height: virtualizer.getTotalSize(), position: 'relative' }}>
          {virtualizer.getVirtualItems().map(vr => {
            const row = rows[vr.index]
            return (
              <div
                key={rowKey(row)}
                data-index={vr.index}
                ref={virtualizer.measureElement}
                className="absolute left-0 w-full grid items-center text-sm border-b border-gray-800 hover:bg-gray-800/40"
                style={{
                  gridTemplateColumns: gridTemplate,
                  transform: `translateY(${vr.start}px)`,
                }}
              >
                {columns.map(col => (
                  <div
                    key={col.key}
                    className={`px-2 py-3 ${ALIGN[col.align ?? 'left']}`}
                  >
                    {col.cell(row)}
                  </div>
                ))}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
