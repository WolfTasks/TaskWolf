import {
  useAdminUsers, useActivateUser, useDeactivateUser, useDeleteUser,
} from '@/hooks/useAdminUsers'
import { DataTable, type Column } from '@/components/table/DataTable'
import { useTranslation } from 'react-i18next'

export function AdminUsersPage() {
  const { t } = useTranslation('admin')
  const { data: users = [], isLoading } = useAdminUsers()
  const activate = useActivateUser()
  const deactivate = useDeactivateUser()
  const del = useDeleteUser()

  function onError(e: any) {
    alert(e.response?.data?.message || t('users.actionFailed'))
  }

  const columns: Column<(typeof users)[number]>[] = [
    { key: 'email', header: t('users.col.email'), cell: u => u.email },
    { key: 'name', header: t('users.col.name'), cell: u => u.displayName },
    { key: 'role', header: t('users.col.role'), width: '120px', cell: u => <span className="text-gray-400">{u.systemRole}</span> },
    {
      key: 'status',
      header: t('users.col.status'),
      width: '120px',
      cell: u => (
        <span className={`px-2 py-0.5 rounded text-xs ${
          u.active ? 'bg-green-900/50 text-green-300' : 'bg-gray-700 text-gray-400'
        }`}>
          {u.active ? t('users.active') : t('users.inactive')}
        </span>
      ),
    },
    {
      key: 'actions',
      header: '',
      width: '200px',
      align: 'right',
      cell: u => (
        <div className="flex gap-2 justify-end">
          {u.active ? (
            <button
              onClick={() => deactivate.mutate(u.id, { onError })}
              className="px-3 py-1 bg-gray-700 hover:bg-gray-600 rounded text-xs"
            >
              {t('users.deactivate')}
            </button>
          ) : (
            <button
              onClick={() => activate.mutate(u.id, { onError })}
              className="px-3 py-1 bg-green-900/40 hover:bg-green-800 text-green-300 rounded text-xs"
            >
              {t('users.activate')}
            </button>
          )}
          <button
            onClick={() => {
              if (confirm(t('users.deleteConfirm', { email: u.email }))) {
                del.mutate(u.id, { onError })
              }
            }}
            className="px-3 py-1 bg-red-900/40 hover:bg-red-800 text-red-300 rounded text-xs"
          >
            {t('delete')}
          </button>
        </div>
      ),
    },
  ]

  if (isLoading) return <div className="text-gray-400">{t('common:loading')}</div>

  return (
    <div className="flex flex-col h-full min-h-0 max-w-3xl">
      <h1 className="text-2xl font-bold mb-6">{t('users.title')}</h1>
      <DataTable
        columns={columns}
        rows={users}
        rowKey={u => u.id}
        empty={t('users.empty')}
      />
    </div>
  )
}
