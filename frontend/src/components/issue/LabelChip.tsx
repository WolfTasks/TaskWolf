import type { Label } from '@/types'

interface Props {
  label: Label
  onClick?: () => void
}

export function LabelChip({ label, onClick }: Props) {
  const base = 'inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium border'
  return (
    <span
      className={`${base} ${onClick ? 'cursor-pointer hover:opacity-80' : ''}`}
      style={{
        backgroundColor: label.color + '26',  // ~15% opacity
        color: label.color,
        borderColor: label.color + '4d',      // ~30% opacity
      }}
      onClick={onClick ? (e: React.MouseEvent) => { e.stopPropagation(); onClick() } : undefined}
    >
      {label.name}
    </span>
  )
}
