import type { Version } from '@/types'

interface Props {
  version: Version
  onClick?: () => void
}

export function VersionChip({ version, onClick }: Props) {
  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-indigo-900 text-indigo-200 border border-indigo-700 ${onClick ? 'cursor-pointer hover:opacity-80' : ''}`}
      onClick={onClick ? (e: React.MouseEvent) => { e.stopPropagation(); onClick() } : undefined}
    >
      {version.name}
    </span>
  )
}
