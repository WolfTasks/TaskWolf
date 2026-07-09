import { useState, useEffect, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { authApi } from '@/api/auth'
import { useUpdateProfile } from '@/hooks/useMe'

export function ProfilePage() {
  const { data: me } = useQuery({ queryKey: ['me'], queryFn: () => authApi.me().then(r => r.data) })
  const update = useUpdateProfile()
  const [displayName, setDisplayName] = useState('')
  const [saved, setSaved] = useState(false)
  const seeded = useRef(false)

  // Seed the form once from the first load; don't let a background refetch
  // (e.g. on window focus) clobber unsaved edits.
  useEffect(() => {
    if (me && !seeded.current) {
      setDisplayName(me.displayName)
      seeded.current = true
    }
  }, [me])

  async function handleSave() {
    if (!displayName.trim()) return
    try {
      await update.mutateAsync(displayName.trim())
      setSaved(true)
      setTimeout(() => setSaved(false), 2000)
    } catch (e: any) {
      alert(e.response?.data?.message || 'Failed to update profile')
    }
  }

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold mb-6">Profile</h1>
      <div className="flex flex-col gap-4">
        <label className="text-sm text-gray-300">
          Email
          <input
            type="email"
            value={me?.email ?? ''}
            readOnly
            className="w-full mt-1 px-3 py-2 bg-gray-900 rounded border border-gray-700 text-sm text-gray-400 cursor-not-allowed"
          />
        </label>
        <label className="text-sm text-gray-300">
          Display name
          <input
            type="text"
            value={displayName}
            onChange={e => setDisplayName(e.target.value)}
            className="w-full mt-1 px-3 py-2 bg-gray-900 rounded border border-gray-600 text-sm"
          />
        </label>
        <div className="flex items-center gap-3">
          <button
            onClick={handleSave}
            disabled={update.isPending || !displayName.trim()}
            className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 rounded text-sm font-medium"
          >
            {update.isPending ? 'Saving…' : 'Save'}
          </button>
          {saved && <span className="text-sm text-green-400">Saved</span>}
        </div>
      </div>
    </div>
  )
}
