import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { organizationsApi } from '@/api/organizations'
import { useOrgMembers, useAddOrgMember, useChangeOrgMemberRole, useRemoveOrgMember } from '@/hooks/useOrganizations'
import { useUserSearch } from '@/hooks/useUserSearch'
import { useMe } from '@/hooks/useAuth'
import { useTranslation } from 'react-i18next'
import type { OrgRole, UserSearchResult } from '@/types'

function memberActionErrorMessage(e: unknown, t: (k: string) => string): string {
  const status = (e as { response?: { status?: number } }).response?.status
  if (status === 403) return t('err.forbidden')
  if (status === 409) return t('err.lastOwner')
  return t('err.updateFailed')
}

function AddOrgMemberForm({ orgId, canGrantOwner }: { orgId: string; canGrantOwner: boolean }) {
  const { t } = useTranslation('orgs')
  const [input, setInput] = useState('')
  const [debounced, setDebounced] = useState('')
  const [selected, setSelected] = useState<UserSearchResult | null>(null)
  const [role, setRole] = useState<OrgRole>('MEMBER')
  const [error, setError] = useState('')

  const addMember = useAddOrgMember(orgId)
  const { data: results = [] } = useUserSearch(debounced)

  useEffect(() => {
    const handle = setTimeout(() => setDebounced(input), 300)
    return () => clearTimeout(handle)
  }, [input])

  const showDropdown = !selected && debounced.trim().length >= 2 && results.length > 0
  const roleOptions: OrgRole[] = canGrantOwner ? ['MEMBER', 'ADMIN', 'OWNER'] : ['MEMBER', 'ADMIN']

  async function handleAdd() {
    if (!selected) return
    try {
      await addMember.mutateAsync({ userId: selected.id, role })
      setInput(''); setDebounced(''); setSelected(null); setRole('MEMBER'); setError('')
    } catch (e: unknown) {
      const status = (e as { response?: { status?: number } }).response?.status
      setError(status === 409 ? t('member.alreadyMember') : t('member.addFailed'))
    }
  }

  return (
    <div className="flex flex-col gap-3 p-4 bg-gray-800 rounded-lg border border-gray-700">
      <div className="relative">
        <label className="block text-xs text-gray-400 mb-1">{t('member.add')}</label>
        <input
          value={selected ? `${selected.displayName} (${selected.email})` : input}
          onChange={e => { setSelected(null); setInput(e.target.value); setError('') }}
          placeholder={t('member.searchPlaceholder')}
          className="w-full bg-gray-700 border border-gray-600 rounded px-3 py-1.5 text-sm text-white outline-none focus:border-blue-500"
        />
        {showDropdown && (
          <ul className="absolute z-10 mt-1 w-full bg-gray-800 border border-gray-600 rounded shadow-lg max-h-56 overflow-auto">
            {results.map(u => (
              <li key={u.id}>
                <button
                  type="button"
                  onClick={() => { setSelected(u); setInput('') }}
                  className="w-full text-left px-3 py-2 text-sm text-white hover:bg-gray-700"
                >
                  <span className="font-medium">{u.displayName}</span>
                  <span className="text-gray-400 ml-2">{u.email}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
      <div className="flex items-center gap-2">
        <select
          value={role}
          onChange={e => setRole(e.target.value as OrgRole)}
          className="bg-gray-700 border border-gray-600 rounded px-2 py-1.5 text-sm text-white"
        >
          {roleOptions.map(r => <option key={r} value={r}>{t(`role.${r}`)}</option>)}
        </select>
        <button
          type="button"
          onClick={handleAdd}
          disabled={!selected || addMember.isPending}
          className="bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white px-4 py-1.5 rounded text-sm font-medium"
        >
          {t('member.addButton')}
        </button>
      </div>
      {error && <p className="text-xs text-red-400">{error}</p>}
    </div>
  )
}

export function OrgSettingsPage() {
  const { t } = useTranslation('orgs')
  const { orgId } = useParams<{ orgId: string }>()
  const { data: me } = useMe()
  const { data: org, isLoading: orgLoading } = useQuery({
    queryKey: ['org', orgId],
    queryFn: () => organizationsApi.getById(orgId!).then(r => r.data),
    enabled: !!orgId,
  })
  const { data: members = [], isLoading: membersLoading } = useOrgMembers(orgId!)
  const changeRole = useChangeOrgMemberRole(orgId!)
  const removeMember = useRemoveOrgMember(orgId!)
  const [actionError, setActionError] = useState('')

  const isSystemAdmin = me?.role === 'ADMIN'
  const myRole = members.find(m => m.user.id === me?.id)?.role
  const canManage = isSystemAdmin || myRole === 'OWNER' || myRole === 'ADMIN'
  const canGrantOwner = isSystemAdmin || myRole === 'OWNER'
  const roleOptions: OrgRole[] = canGrantOwner ? ['MEMBER', 'ADMIN', 'OWNER'] : ['MEMBER', 'ADMIN']

  if (orgLoading) return <div className="p-6 text-gray-400">{t('common:loading')}</div>
  if (!org) return <div className="p-6 text-red-400">{t('notFound')}</div>

  async function handleRemove(userId: string, name: string) {
    if (!confirm(t('member.removeConfirm', { name, org: org!.name }))) return
    try {
      await removeMember.mutateAsync(userId)
      setActionError('')
    } catch (e: unknown) {
      setActionError(memberActionErrorMessage(e, t))
    }
  }

  return (
    <div className="p-6 max-w-2xl mx-auto space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">{t('settingsTitle', { name: org.name })}</h1>
        <p className="text-gray-400 text-sm">{t('slugLabel')}: {org.slug}</p>
      </div>

      {canManage && <AddOrgMemberForm orgId={orgId!} canGrantOwner={canGrantOwner} />}

      <div className="flex flex-col gap-2">
        <h2 className="text-lg font-medium">{t('membersTitle')}</h2>
        {actionError && <p className="text-xs text-red-400">{actionError}</p>}
        {membersLoading && <p className="text-gray-400 text-sm">{t('common:loading')}</p>}
        {!membersLoading && members.length === 0 && (
          <p className="text-gray-500 text-sm">{t('noMembers')}</p>
        )}
        {members.map(({ user, role }) => {
          const isSelf = user.id === me?.id
          const isOwner = role === 'OWNER'
          // Only system admins may touch OWNER rows; nobody may edit their own row.
          const lockRole = isSelf || (isOwner && !isSystemAdmin) || !canManage
          const lockRemove = isSelf || (isOwner && !isSystemAdmin) || !canManage
          return (
            <div key={user.id} className="flex items-center gap-3 px-4 py-3 bg-gray-900 border border-gray-800 rounded-lg">
              <div className="min-w-0">
                <div className="text-sm text-white truncate">{user.displayName}</div>
                <div className="text-xs text-gray-500 truncate">{user.email}</div>
              </div>
              {isOwner && <span className="text-xs bg-gray-700 text-gray-300 px-2 py-0.5 rounded">{t('member.owner')}</span>}
              {isSelf && <span className="text-xs bg-gray-700 text-gray-300 px-2 py-0.5 rounded">{t('member.you')}</span>}
              <div className="ml-auto flex items-center gap-2">
                <select
                  value={role}
                  disabled={lockRole || changeRole.isPending}
                  onChange={e => changeRole.mutate(
                    { userId: user.id, role: e.target.value as OrgRole },
                    {
                      onSuccess: () => setActionError(''),
                      onError: (err: unknown) => setActionError(memberActionErrorMessage(err, t)),
                    }
                  )}
                  className="bg-gray-700 border border-gray-600 rounded px-2 py-1 text-sm text-white disabled:opacity-50"
                >
                  {/* Ensure the current role renders even if outside the actor's grantable set. */}
                  {(roleOptions.includes(role) ? roleOptions : [role, ...roleOptions]).map(r => (
                    <option key={r} value={r}>{t(`role.${r}`)}</option>
                  ))}
                </select>
                {canManage && (
                  <button
                    onClick={() => handleRemove(user.id, user.displayName)}
                    disabled={lockRemove}
                    className="text-xs text-red-400 hover:text-red-300 disabled:opacity-30 px-2 py-1 rounded hover:bg-gray-700"
                  >
                    {t('member.remove')}
                  </button>
                )}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
