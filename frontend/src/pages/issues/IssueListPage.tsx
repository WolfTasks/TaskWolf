import { useState } from 'react'
import { useParams, Link, useSearchParams } from 'react-router-dom'
import { useIssues, useCreateIssue } from '@/hooks/useIssues'
import { useLabels } from '@/hooks/useLabels'
import { StatusBadge } from '@/components/issue/StatusBadge'

export function IssueListPage() {
  const { key } = useParams<{ key: string }>()
  const [searchParams, setSearchParams] = useSearchParams()
  const labelId = searchParams.get('labelId') ?? undefined

  const { data: page, isLoading } = useIssues(key!, labelId)
  const { data: labels = [] } = useLabels(key!)
  const createIssue = useCreateIssue(key!)
  const [title, setTitle] = useState('')
  const [showForm, setShowForm] = useState(false)

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!title.trim()) return
    await createIssue.mutateAsync({ title })
    setTitle('')
    setShowForm(false)
  }

  function setLabelFilter(id: string | undefined) {
    if (id) setSearchParams({ labelId: id })
    else setSearchParams({})
  }

  if (isLoading) return <div className="text-gray-400">Loading...</div>

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-2xl font-bold">{key} — Issues</h1>
        <button onClick={() => setShowForm(true)}
          className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded text-sm font-medium">
          + Create Issue
        </button>
      </div>

      {/* Toolbar filters */}
      <div className="flex items-center gap-3 mb-4">
        <select
          value={labelId ?? ''}
          onChange={e => setLabelFilter(e.target.value || undefined)}
          className="bg-gray-800 border border-gray-700 text-sm text-white rounded px-3 py-1.5 outline-none"
        >
          <option value="">All Labels</option>
          {labels.map(l => (
            <option key={l.id} value={l.id}>{l.name}</option>
          ))}
        </select>
        {labelId && (
          <button
            onClick={() => setLabelFilter(undefined)}
            className="text-xs text-gray-400 hover:text-white"
          >
            ✕ Clear
          </button>
        )}
      </div>

      {showForm && (
        <form onSubmit={handleCreate} className="mb-4 flex gap-2">
          <input value={title} onChange={e => setTitle(e.target.value)} placeholder="Issue title" autoFocus required
            className="flex-1 bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm" />
          <button type="submit" className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded text-sm">Save</button>
          <button type="button" onClick={() => setShowForm(false)} className="text-gray-400 hover:text-white px-3 py-2 text-sm">Cancel</button>
        </form>
      )}

      <div className="flex flex-col gap-2">
        {page?.content.map(issue => (
          <Link key={issue.id} to={`/p/${key}/issues/${issue.key}`}
            className="bg-gray-900 border border-gray-800 hover:border-gray-600 rounded-lg px-4 py-3 flex items-center gap-4">
            <span className="text-xs text-gray-500 font-mono w-20">{issue.key}</span>
            <span className="flex-1 text-sm text-white">{issue.title}</span>
            <StatusBadge name={issue.statusName} category={issue.statusCategory} />
            <span className={`text-xs px-2 py-0.5 rounded font-medium ${
              issue.priority === 'CRITICAL' ? 'text-red-400' :
              issue.priority === 'HIGH' ? 'text-orange-400' :
              issue.priority === 'MEDIUM' ? 'text-yellow-400' : 'text-green-400'
            }`}>{issue.priority}</span>
          </Link>
        ))}
        {page?.content.length === 0 && (
          <p className="text-gray-500 text-sm py-8 text-center">No issues yet. Create your first one!</p>
        )}
      </div>
    </div>
  )
}
