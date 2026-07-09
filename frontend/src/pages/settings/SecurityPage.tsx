import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useChangePassword } from '@/hooks/useMe'

export function SecurityPage() {
  const change = useChangePassword()
  const navigate = useNavigate()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')

  async function handleSubmit() {
    setError('')
    if (next.length < 8) { setError('New password must be at least 8 characters'); return }
    if (next !== confirm) { setError('New passwords do not match'); return }
    try {
      await change.mutateAsync({ currentPassword: current, newPassword: next })
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      navigate('/login')
    } catch (e: any) {
      setError(e.response?.data?.message || 'Failed to change password')
    }
  }

  const inputClass = 'w-full mt-1 px-3 py-2 bg-gray-900 rounded border border-gray-600 text-sm'

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold mb-6">Security</h1>
      <div className="flex flex-col gap-4">
        <label className="text-sm text-gray-300">
          Current password
          <input type="password" value={current} onChange={e => setCurrent(e.target.value)} className={inputClass} />
        </label>
        <label className="text-sm text-gray-300">
          New password
          <input type="password" value={next} onChange={e => setNext(e.target.value)} className={inputClass} />
        </label>
        <label className="text-sm text-gray-300">
          Confirm new password
          <input type="password" value={confirm} onChange={e => setConfirm(e.target.value)} className={inputClass} />
        </label>
        {error && <p className="text-sm text-red-400">{error}</p>}
        <p className="text-xs text-gray-500">
          Changing your password signs you out of all sessions. Personal access tokens keep working.
        </p>
        <button
          onClick={handleSubmit}
          disabled={change.isPending || !current || !next || !confirm}
          className="self-start px-4 py-2 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 rounded text-sm font-medium"
        >
          {change.isPending ? 'Changing…' : 'Change password'}
        </button>
      </div>
    </div>
  )
}
