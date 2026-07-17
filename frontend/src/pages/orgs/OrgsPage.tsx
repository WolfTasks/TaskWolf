import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { organizationsApi, CreateOrganizationRequest, Organization } from '@/api/organizations'
import { useMe } from '@/hooks/useAuth'
import { useTranslation } from 'react-i18next'

const emptyForm: CreateOrganizationRequest = { name: '', slug: '' }

function OrgList({ orgs, empty }: { orgs: Organization[]; empty: string }) {
  const { t } = useTranslation('orgs')
  if (orgs.length === 0) return <p className="text-gray-500 text-sm">{empty}</p>
  return (
    <>
      {orgs.map(org => (
        <div key={org.id} className="bg-gray-900 border border-gray-800 rounded-lg p-4 flex items-center justify-between">
          <div>
            <div className="font-medium text-sm">{org.name}</div>
            <div className="text-xs text-gray-400">{org.slug}</div>
          </div>
          <Link to={`/orgs/${org.id}/settings`} className="text-blue-400 hover:text-blue-300 text-sm">{t('list.settings')}</Link>
        </div>
      ))}
    </>
  )
}

export function OrgsPage() {
  const { t } = useTranslation('orgs')
  const queryClient = useQueryClient()
  const [form, setForm] = useState<CreateOrganizationRequest>(emptyForm)
  const [formError, setFormError] = useState('')
  const { data: me } = useMe()
  const isAdmin = me?.role === 'ADMIN'

  const { data: myOrgs = [], isLoading: mineLoading } = useQuery({
    queryKey: ['organizations', 'mine'],
    queryFn: () => organizationsApi.listMine().then(r => r.data),
  })

  const { data: allOrgs = [], isLoading: allLoading } = useQuery({
    queryKey: ['organizations'],
    queryFn: () => organizationsApi.listAll().then(r => r.data),
    enabled: isAdmin,
  })

  const createMutation = useMutation({
    mutationFn: (data: CreateOrganizationRequest) => organizationsApi.create(data).then(r => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizations'] })
      setForm(emptyForm); setFormError('')
    },
    onError: () => setFormError(t('create.error')),
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setFormError('')
    if (!form.name || !form.slug) { setFormError(t('create.required')); return }
    createMutation.mutate(form)
  }

  return (
    <div className="p-6 max-w-3xl mx-auto space-y-8">
      <h1 className="text-2xl font-semibold">{t('title')}</h1>

      <section className="space-y-3">
        <h2 className="text-lg font-medium">{t('myOrgs')}</h2>
        {mineLoading && <p className="text-gray-400 text-sm">{t('common:loading')}</p>}
        {!mineLoading && <OrgList orgs={myOrgs} empty={t('emptyMine')} />}
      </section>

      {isAdmin && (
        <>
          <section className="space-y-4">
            <h2 className="text-lg font-medium">{t('create.title')}</h2>
            <form onSubmit={handleSubmit} className="space-y-3">
              {formError && <p className="text-red-400 text-sm">{formError}</p>}
              <div className="flex flex-col gap-1">
                <label className="text-sm text-gray-400">{t('create.nameLabel')}</label>
                <input
                  type="text"
                  value={form.name}
                  onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
                  placeholder={t('create.namePlaceholder')}
                  className="bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm"
                />
              </div>
              <div className="flex flex-col gap-1">
                <label className="text-sm text-gray-400">{t('create.slugLabel')}</label>
                <input
                  type="text"
                  value={form.slug}
                  onChange={e => setForm(f => ({ ...f, slug: e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, '-') }))}
                  placeholder={t('create.slugPlaceholder')}
                  className="bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm"
                />
              </div>
              <button
                type="submit"
                disabled={createMutation.isPending}
                className="bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded px-4 py-2 text-sm font-medium"
              >
                {createMutation.isPending ? t('create.submitting') : t('create.title')}
              </button>
            </form>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-medium">{t('allOrgs')}</h2>
            {allLoading && <p className="text-gray-400 text-sm">{t('common:loading')}</p>}
            {!allLoading && <OrgList orgs={allOrgs} empty={t('emptyAll')} />}
          </section>
        </>
      )}
    </div>
  )
}
