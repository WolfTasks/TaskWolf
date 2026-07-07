import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useDeleteAccount } from '@/hooks/useAccount'

export function AccountSettingsPage() {
  const del = useDeleteAccount()
  const navigate = useNavigate()
  const [confirming, setConfirming] = useState(false)

  async function handleDelete() {
    try {
      await del.mutateAsync()
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      navigate('/login')
    } catch (e: any) {
      alert(e.response?.data?.message || 'Failed to delete account')
    }
  }

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold mb-6">Account</h1>
      <div className="p-4 bg-red-900/20 border border-red-800 rounded">
        <h2 className="text-sm font-semibold text-red-400 mb-2">Delete account</h2>
        <p className="text-sm text-gray-400 mb-3">
          Your account will be deactivated and anonymized, and all your access tokens
          will be revoked. This cannot be undone.
        </p>
        {confirming ? (
          <div className="flex gap-2">
            <button
              onClick={handleDelete}
              disabled={del.isPending}
              className="px-4 py-2 bg-red-700 hover:bg-red-600 rounded text-sm"
            >
              {del.isPending ? 'Deleting…' : 'Yes, delete my account'}
            </button>
            <button
              onClick={() => setConfirming(false)}
              className="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm"
            >
              Cancel
            </button>
          </div>
        ) : (
          <button
            onClick={() => setConfirming(true)}
            className="px-4 py-2 bg-red-900/40 hover:bg-red-800 text-red-300 rounded text-sm"
          >
            Delete account
          </button>
        )}
      </div>
    </div>
  )
}
