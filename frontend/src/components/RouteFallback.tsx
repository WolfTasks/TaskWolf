import { Loader2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'

export function RouteFallback() {
  const { t } = useTranslation()
  return (
    <div className="flex h-full min-h-40 items-center justify-center text-gray-500">
      <Loader2 className="animate-spin" size={28} aria-label={t('common:loading')} />
    </div>
  )
}
