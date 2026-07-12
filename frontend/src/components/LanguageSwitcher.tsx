import { useTranslation } from 'react-i18next'
import { SUPPORTED_LANGUAGES } from '@/i18n'
import { useUpdateLanguage } from '@/hooks/useMe'

const LABELS: Record<string, string> = { en: 'English', de: 'Deutsch' }

export function LanguageSwitcher() {
  const { i18n, t } = useTranslation('settings')
  const updateLanguage = useUpdateLanguage()

  const onChange = (lng: string) => {
    void i18n.changeLanguage(lng)          // writes localStorage + updates <html lang> immediately
    updateLanguage.mutate(lng)             // best-effort backend persistence; UI never blocks
  }

  return (
    <label className="text-sm text-gray-300">
      {t('language.label')}
      <select
        value={i18n.language.split('-')[0]}
        onChange={e => onChange(e.target.value)}
        className="w-full mt-1 px-3 py-2 bg-gray-900 rounded border border-gray-600 text-sm"
      >
        {SUPPORTED_LANGUAGES.map(lng => (
          <option key={lng} value={lng}>{LABELS[lng]}</option>
        ))}
      </select>
    </label>
  )
}
