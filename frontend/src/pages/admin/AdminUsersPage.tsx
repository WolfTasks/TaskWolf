import {
  useAdminUsers, useActivateUser, useDeactivateUser, useDeleteUser,
} from '@/hooks/useAdminUsers'
import { DataTable, type Column } from '@/components/table/DataTable'

export function AdminUsersPage() {
  const { data: users = [], isLoading } = useAdminUsers()
  const activate = useActivateUser()
  const deactivate = useDeactivateUser()
  const del = useDeleteUser()

  function onError(e: any) {
    alert(e.response?.data?.message || 'Action failed')
  }

  const columns: Column<(typeof users)[number]>[] = [
    { key: 'email', header: 'Email', cell: u => u.email },
    { key: 'name', header: 'Name', cell: u => u.displayName },
    { key: 'role', header: 'Role', width: '120px', cell: u => <span className="text-gray-400">{u.systemRole}</span> },
    {
      key: 'status',
      header: 'Status',
      width: '120px',
      cell: u => (
        <span className={`px-2 py-0.5 rounded text-xs ${
          u.active ? 'bg-green-900/50 text-green-300' : 'bg-gray-700 text-gray-400'
        }`}>
          {u.active ? 'Active' : 'Inactive'}
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
              Deactivate
            </button>
          ) : (
            <button
              onClick={() => activate.mutate(u.id, { onError })}
              className="px-3 py-1 bg-green-900/40 hover:bg-green-800 text-green-300 rounded text-xs"
            >
              Activate
            </button>
          )}
          <button
            onClick={() => {
              if (confirm(`Delete ${u.email}? This anonymizes the account.`)) {
                del.mutate(u.id, { onError })
              }
            }}
            className="px-3 py-1 bg-red-900/40 hover:bg-red-800 text-red-300 rounded text-xs"
          >
            Delete
          </button>
        </div>
      ),
    },
  ]

  if (isLoading) return <div className="text-gray-400">Loading…</div>

  return (
    <div className="flex flex-col h-full min-h-0 max-w-3xl">
      <h1 className="text-2xl font-bold mb-6">Users</h1>
      <DataTable
        columns={columns}
        rows={users}
        rowKey={u => u.id}
        empty="No users"
      />
    </div>
  )
}
