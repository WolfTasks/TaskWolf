import { useParams, useNavigate } from 'react-router-dom'
import { useSprints } from '@/hooks/useSprints'
import { useTranslation } from 'react-i18next'
import { SprintCard } from '@/components/sprint/SprintCard'
import type { Sprint } from '@/types'

export function SprintsPage() {
  const { t } = useTranslation('sprints')
  const { key } = useParams<{ key: string }>()
  const navigate = useNavigate()
  const { data: sprints, isLoading } = useSprints(key!)

  if (isLoading) return <div className="text-gray-400">{t('common:loading')}</div>

  const all = sprints ?? []
  const active = all.filter(s => s.status === 'ACTIVE')
  const planned = all.filter(s => s.status === 'PLANNED')
  const closed = all
    .filter(s => s.status === 'CLOSED')
    .sort((a, b) => (b.endDate ?? '').localeCompare(a.endDate ?? ''))

  const go = (target: string) => () => navigate(`/p/${key}/${target}`)

  const renderSection = (title: string, items: Sprint[], target: string, emptyHint: string) => (
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
      <h1 className="text-2xl font-bold mb-6">{t('title')}</h1>
      {renderSection(t('section.active'), active, 'board', t('empty.active'))}
      {renderSection(t('section.planned'), planned, 'backlog', t('empty.planned'))}
      {renderSection(t('section.closed'), closed, 'reports', t('empty.closed'))}
    </div>
  )
}
