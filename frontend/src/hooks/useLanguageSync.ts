import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { authApi } from '@/api/auth'
import { SUPPORTED_LANGUAGES } from '@/i18n'

// Applies the server-stored language once after /me loads, so the choice
// follows the user to a new device even when localStorage is empty there.
export function useLanguageSync() {
  const { i18n } = useTranslation()
  const applied = useRef(false)
  const { data: me } = useQuery({
    queryKey: ['me'],
    queryFn: () => authApi.me().then(r => r.data),
  })

  useEffect(() => {
    if (applied.current || !me?.language) return
    applied.current = true
    const lng = me.language.split('-')[0]
    if ((SUPPORTED_LANGUAGES as readonly string[]).includes(lng) && lng !== i18n.language.split('-')[0]) {
      void i18n.changeLanguage(lng)
    }
  }, [me, i18n])
}
