import { Link } from 'react-router-dom'
import { useProjects } from '@/hooks/useProjects'

export function DashboardPage() {
  const { data: projects, isLoading } = useProjects()
  if (isLoading) return <div className="text-gray-400">Loading...</div>

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Projects</h1>
        <Link to="/projects/new"
          className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded text-sm font-medium">
          New Project
        </Link>
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {projects?.map(p => (
          <Link key={p.id} to={`/p/${p.key}/issues`}
            className="bg-gray-900 border border-gray-800 rounded-lg p-5 hover:border-gray-600 transition-colors">
            <div className="flex items-center gap-2 mb-2">
              <span className="text-xs font-bold text-blue-400 bg-blue-900/30 px-2 py-0.5 rounded">{p.key}</span>
            </div>
            <h2 className="font-semibold text-white">{p.name}</h2>
            {p.description && <p className="text-sm text-gray-400 mt-1 line-clamp-2">{p.description}</p>}
          </Link>
        ))}
      </div>
    </div>
  )
}
