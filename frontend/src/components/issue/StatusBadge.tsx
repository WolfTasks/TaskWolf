import { cn } from '@/lib/utils'

const categoryColors = {
  TODO: 'bg-blue-900 text-blue-300',
  IN_PROGRESS: 'bg-yellow-900 text-yellow-300',
  DONE: 'bg-green-900 text-green-300',
}

interface Props {
  name: string
  category: 'TODO' | 'IN_PROGRESS' | 'DONE'
}

export function StatusBadge({ name, category }: Props) {
  return (
    <span className={cn('px-2 py-0.5 rounded text-xs font-medium', categoryColors[category])}>
      {name}
    </span>
  )
}
