import {
  useAdminUsers, useActivateUser, useDeactivateUser, useDeleteUser,
} from '@/hooks/useAdminUsers'

export function AdminUsersPage() {
  const { data: users = [], isLoading } = useAdminUsers()
  const activate = useActivateUser()
  const deactivate = useDeactivateUser()
  const del = useDeleteUser()

  function onError(e: any) {
    alert(e.response?.data?.message || 'Action failed')
  }

  if (isLoading) return <div className="text-gray-400">Loading…</div>

  return (
    <div className="max-w-3xl">
      <h1 className="text-2xl font-bold mb-6">Users</h1>
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-gray-400 border-b border-gray-700">
            <th className="pb-2">Email</th>
            <th className="pb-2">Name</th>
            <th className="pb-2">Role</th>
            <th className="pb-2">Status</th>
            <th className="pb-2"></th>
          </tr>
        </thead>
        <tbody>
          {users.map(u => (
            <tr key={u.id} className="border-b border-gray-800">
              <td className="py-3">{u.email}</td>
              <td className="py-3">{u.displayName}</td>
              <td className="py-3 text-gray-400">{u.systemRole}</td>
              <td className="py-3">
                <span className={`px-2 py-0.5 rounded text-xs ${
                  u.active ? 'bg-green-900/50 text-green-300' : 'bg-gray-700 text-gray-400'
                }`}>
                  {u.active ? 'Active' : 'Inactive'}
                </span>
              </td>
              <td className="py-3 text-right flex gap-2 justify-end">
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
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
