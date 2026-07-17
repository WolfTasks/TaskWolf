import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useCreateProject } from '@/hooks/useProjects'
import { useTranslation } from 'react-i18next'

export function ProjectCreatePage() {
  const { t } = useTranslation('projects')
  const navigate = useNavigate()
  const createProject = useCreateProject()
  const [form, setForm] = useState({ key: '', name: '', description: '' })
  const [error, setError] = useState('')

  const set = (f: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
    setForm(prev => ({ ...prev, [f]: e.target.value }))

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    try {
      const project = await createProject.mutateAsync(form)
      navigate(`/p/${project.key}/issues`)
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } }
      setError(axiosErr.response?.data?.message ?? t('create.error'))
    }
  }

  return (
    <div className="max-w-lg">
      <h1 className="text-2xl font-bold mb-6">{t('create.title')}</h1>
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        {error && <p className="text-red-400 text-sm">{error}</p>}
        <div>
          <label className="block text-sm text-gray-400 mb-1">{t('create.keyLabel')} <span className="text-gray-500">{t('create.keyHint')}</span></label>
          <input value={form.key} onChange={set('key')} placeholder={t('create.keyPlaceholder')} required
            pattern="[A-Z0-9]+" minLength={2} maxLength={10}
            className="w-full bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm font-mono uppercase" />
        </div>
        <div>
          <label className="block text-sm text-gray-400 mb-1">{t('create.nameLabel')}</label>
          <input value={form.name} onChange={set('name')} placeholder={t('create.namePlaceholder')} required
            className="w-full bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm" />
        </div>
        <div>
          <label className="block text-sm text-gray-400 mb-1">{t('create.descLabel')} <span className="text-gray-600">{t('create.optional')}</span></label>
          <textarea value={form.description} onChange={set('description')} rows={3}
            className="w-full bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm resize-none" />
        </div>
        <button type="submit" disabled={createProject.isPending}
          className="bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded px-4 py-2 text-sm font-medium">
          {createProject.isPending ? t('create.submitting') : t('create.submit')}
        </button>
      </form>
    </div>
  )
}
