import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'

import enCommon from './locales/en/common.json'
import deCommon from './locales/de/common.json'
import enSettings from './locales/en/settings.json'
import deSettings from './locales/de/settings.json'
import enNav from './locales/en/nav.json'
import deNav from './locales/de/nav.json'

export const SUPPORTED_LANGUAGES = ['en', 'de'] as const
export type AppLanguage = (typeof SUPPORTED_LANGUAGES)[number]

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      en: { common: enCommon, settings: enSettings, nav: enNav },
      de: { common: deCommon, settings: deSettings, nav: deNav },
    },
    fallbackLng: 'en',
    supportedLngs: [...SUPPORTED_LANGUAGES],
    nonExplicitSupportedLngs: true,
    defaultNS: 'common',
    ns: ['common', 'settings', 'nav'],
    interpolation: { escapeValue: false },
    detection: {
      order: ['localStorage', 'navigator'],
      lookupLocalStorage: 'taskowolf.lang',
      caches: ['localStorage'],
    },
    debug: import.meta.env.DEV,
  })

i18n.on('languageChanged', (lng) => {
  document.documentElement.lang = lng
})
document.documentElement.lang = i18n.language

export default i18n
