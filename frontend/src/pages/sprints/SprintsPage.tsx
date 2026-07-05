import { useParams, useNavigate } from 'react-router-dom'
import { useSprints } from '@/hooks/useSprints'
import { SprintCard } from '@/components/sprint/SprintCard'
import type { Sprint } from '@/types'

export function SprintsPage() {
  const { key } = useParams<{ key: string }>()
  const navigate = useNavigate()
  const { data: sprints, isLoading } = useSprints(key!)

  if (isLoading) return <div className="text-gray-400">Loading...</div>

  const all = sprints ?? []
  const active = all.filter(s => s.status === 'ACTIVE')
  const planned = all.filter(s => s.status === 'PLANNED')
  const closed = all
    .filter(s => s.status === 'CLOSED')
    .sort((a, b) => (b.endDate ?? '').localeCompare(a.endDate ?? ''))

  const go = (target: string) => () => navigate(`/p/${key}/${target}`)

  const Section = ({
    title,
    items,
    target,
    emptyHint,
  }: {
    title: string
    items: Sprint[]
    target: string
    emptyHint: string
  }) => (
    <section className="mb-8">
      <h2 className="text-lg font-semibold text-white mb-3">{title}</h2>
      {items.length === 0 ? (
        <p className="text-gray-500 text-sm">{emptyHint}</p>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2">
          {items.map(s => (
            <SprintCard key={s.id} sprint={s} onClick={go(target)} />
          ))}
        </div>
      )}
    </section>
  )

  return (
    <div className="max-w-4xl">
      <h1 className="text-2xl font-bold mb-6">Sprints</h1>
      <Section title="Laufend" items={active} target="board" emptyHint="Kein laufender Sprint." />
      <Section title="Geplant" items={planned} target="backlog" emptyHint="Keine geplanten Sprints." />
      <Section title="Abgeschlossen" items={closed} target="reports" emptyHint="Noch keine abgeschlossenen Sprints." />
    </div>
  )
}
