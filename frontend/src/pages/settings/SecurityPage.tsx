import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useChangePassword } from '@/hooks/useMe'

export function SecurityPage() {
  const { t } = useTranslation('settings')
  const change = useChangePassword()
  const navigate = useNavigate()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')

  async function handleSubmit() {
    setError('')
    if (next.length < 8) { setError(t('security.passwordTooShort')); return }
    if (next !== confirm) { setError(t('security.passwordMismatch')); return }
    try {
      await change.mutateAsync({ currentPassword: current, newPassword: next })
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      navigate('/login')
    } catch (e: any) {
      setError(e.response?.data?.message || t('security.changeFailed'))
    }
  }

  const inputClass = 'w-full mt-1 px-3 py-2 bg-gray-900 rounded border border-gray-600 text-sm'

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold mb-6">{t('security.title')}</h1>
      <div className="flex flex-col gap-4">
        <label className="text-sm text-gray-300">
          {t('security.currentPassword')}
          <input type="password" value={current} onChange={e => setCurrent(e.target.value)} className={inputClass} />
        </label>
        <label className="text-sm text-gray-300">
          {t('security.newPassword')}
          <input type="password" value={next} onChange={e => setNext(e.target.value)} className={inputClass} />
        </label>
        <label className="text-sm text-gray-300">
          {t('security.confirmNewPassword')}
          <input type="password" value={confirm} onChange={e => setConfirm(e.target.value)} className={inputClass} />
        </label>
        {error && <p className="text-sm text-red-400">{error}</p>}
        <p className="text-xs text-gray-500">
          {t('security.signOutWarning')}
        </p>
        <button
          onClick={handleSubmit}
          disabled={change.isPending || !current || !next || !confirm}
          className="self-start px-4 py-2 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 rounded text-sm font-medium"
        >
          {change.isPending ? t('security.changing') : t('security.changePassword')}
        </button>
      </div>
    </div>
  )
}
