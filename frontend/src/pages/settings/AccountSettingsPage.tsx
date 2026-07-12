import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useDeleteAccount } from '@/hooks/useAccount'

export function AccountSettingsPage() {
  const { t } = useTranslation('settings')
  const { t: tc } = useTranslation('common')
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
      alert(e.response?.data?.message || t('account.deleteFailed'))
    }
  }

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold mb-6">{t('account.title')}</h1>
      <div className="p-4 bg-red-900/20 border border-red-800 rounded">
        <h2 className="text-sm font-semibold text-red-400 mb-2">{t('account.deleteAccount')}</h2>
        <p className="text-sm text-gray-400 mb-3">
          {t('account.deleteWarning')}
        </p>
        {confirming ? (
          <div className="flex gap-2">
            <button
              onClick={handleDelete}
              disabled={del.isPending}
              className="px-4 py-2 bg-red-700 hover:bg-red-600 rounded text-sm"
            >
              {del.isPending ? t('account.deleting') : t('account.confirmDelete')}
            </button>
            <button
              onClick={() => setConfirming(false)}
              className="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm"
            >
              {tc('cancel')}
            </button>
          </div>
        ) : (
          <button
            onClick={() => setConfirming(true)}
            className="px-4 py-2 bg-red-900/40 hover:bg-red-800 text-red-300 rounded text-sm"
          >
            {t('account.deleteAccount')}
          </button>
        )}
      </div>
    </div>
  )
}
