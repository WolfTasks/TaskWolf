import { useState, useEffect } from 'react'
import { useParams, Link, useSearchParams } from 'react-router-dom'
import { useIssues, useCreateIssue } from '@/hooks/useIssues'
import { useLabels } from '@/hooks/useLabels'
import { useVersions } from '@/hooks/useVersions'
import { useCustomFields } from '@/hooks/useCustomFields'
import { StatusBadge } from '@/components/issue/StatusBadge'

export function IssueListPage() {
  const { key } = useParams<{ key: string }>()
  const [searchParams, setSearchParams] = useSearchParams()
  const labelId = searchParams.get('labelId') ?? undefined
  const fixVersionId = searchParams.get('fixVersionId') ?? undefined
  const affectsVersionId = searchParams.get('affectsVersionId') ?? undefined

  const { data: customFieldDefs = [] } = useCustomFields(key!)

  // Build customFieldFilters map from URL params (key = fieldId, value = raw filter value)
  const customFieldFilters: Record<string, string> = {}
  customFieldDefs.forEach(def => {
    const v = searchParams.get(`cf_${def.id}`)
    if (v) customFieldFilters[def.id] = v
  })

  const { data: page, isLoading } = useIssues(key!, { labelId, fixVersionId, affectsVersionId, customFieldFilters: Object.keys(customFieldFilters).length > 0 ? customFieldFilters : undefined })
  const { data: labels = [] } = useLabels(key!)
  const { data: versions = [] } = useVersions(key!)
  const createIssue = useCreateIssue(key!)
  const [title, setTitle] = useState('')
  const [showForm, setShowForm] = useState(false)

  useEffect(() => {
    if (labelId && labels.length > 0 && !labels.some(l => l.id === labelId)) {
      setSearchParams(prev => { const n = new URLSearchParams(prev); n.delete('labelId'); return n })
    }
  }, [labelId, labels, setSearchParams])

  // Backend prioritises version filters over labelId — auto-clear label when a version filter is active
  useEffect(() => {
    if (labelId && (fixVersionId || affectsVersionId)) {
      setSearchParams(prev => { const n = new URLSearchParams(prev); n.delete('labelId'); return n })
    }
  }, [labelId, fixVersionId, affectsVersionId, setSearchParams])

  useEffect(() => {
    if (fixVersionId && versions.length > 0 && !versions.some(v => v.id === fixVersionId)) {
      setSearchParams(prev => { const n = new URLSearchParams(prev); n.delete('fixVersionId'); return n })
    }
  }, [fixVersionId, versions, setSearchParams])

  useEffect(() => {
    if (affectsVersionId && versions.length > 0 && !versions.some(v => v.id === affectsVersionId)) {
      setSearchParams(prev => { const n = new URLSearchParams(prev); n.delete('affectsVersionId'); return n })
    }
  }, [affectsVersionId, versions, setSearchParams])

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!title.trim()) return
    await createIssue.mutateAsync({ title })
    setTitle('')
    setShowForm(false)
  }

  function setParam(key: string, value: string | undefined) {
    setSearchParams(prev => {
      const n = new URLSearchParams(prev)
      if (value) n.set(key, value)
      else n.delete(key)
      return n
    })
  }

  function setCfParam(fieldId: string, value: string | undefined) {
    setSearchParams(prev => {
      const n = new URLSearchParams(prev)
      if (value) n.set(`cf_${fieldId}`, value)
      else n.delete(`cf_${fieldId}`)
      return n
    })
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
      <div className="flex items-center gap-3 mb-4 flex-wrap">
        <select
          value={labelId ?? ''}
          onChange={e => setParam('labelId', e.target.value || undefined)}
          className="bg-gray-800 border border-gray-700 text-sm text-white rounded px-3 py-1.5 outline-none"
        >
          <option value="">All Labels</option>
          {labels.map(l => (
            <option key={l.id} value={l.id}>{l.name}</option>
          ))}
        </select>

        <select
          value={fixVersionId ?? ''}
          onChange={e => setParam('fixVersionId', e.target.value || undefined)}
          className="bg-gray-800 border border-gray-700 text-sm text-white rounded px-3 py-1.5 outline-none"
        >
          <option value="">All Fix Versions</option>
          {versions.map(v => (
            <option key={v.id} value={v.id}>{v.name}</option>
          ))}
        </select>

        <select
          value={affectsVersionId ?? ''}
          onChange={e => setParam('affectsVersionId', e.target.value || undefined)}
          className="bg-gray-800 border border-gray-700 text-sm text-white rounded px-3 py-1.5 outline-none"
        >
          <option value="">All Affects Versions</option>
          {versions.map(v => (
            <option key={v.id} value={v.id}>{v.name}</option>
          ))}
        </select>

        {customFieldDefs.map(def => (
          <div key={def.id} className="flex flex-col">
            <span className="text-xs text-gray-500 mb-0.5">{def.name}</span>
            {def.type === 'DROPDOWN' ? (
              <select
                value={customFieldFilters[def.id] ?? ''}
                onChange={e => setCfParam(def.id, e.target.value || undefined)}
                className="bg-gray-800 border border-gray-700 text-sm text-white rounded px-3 py-1.5 outline-none"
              >
                <option value="">All</option>
                {def.options?.map(opt => <option key={opt.id} value={opt.id}>{opt.label}</option>)}
              </select>
            ) : def.type === 'CHECKBOX' ? (
              <select
                value={customFieldFilters[def.id] ?? ''}
                onChange={e => setCfParam(def.id, e.target.value || undefined)}
                className="bg-gray-800 border border-gray-700 text-sm text-white rounded px-3 py-1.5 outline-none"
              >
                <option value="">All</option>
                <option value="true">Yes</option>
                <option value="false">No</option>
              </select>
            ) : (
              <input
                type={def.type === 'NUMBER' ? 'number' : def.type === 'DATE' ? 'date' : 'text'}
                value={customFieldFilters[def.id] ?? ''}
                onChange={e => setCfParam(def.id, e.target.value || undefined)}
                placeholder={`Filter by ${def.name}`}
                className="bg-gray-800 border border-gray-700 text-sm text-white rounded px-3 py-1.5 outline-none"
              />
            )}
          </div>
        ))}

        {(labelId || fixVersionId || affectsVersionId || Object.keys(customFieldFilters).length > 0) && (
          <button
            onClick={() => setSearchParams({})}
            className="text-xs text-gray-400 hover:text-white"
          >
            ✕ Clear filters
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
