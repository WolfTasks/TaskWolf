import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'

import enCommon from './locales/en/common.json'
import deCommon from './locales/de/common.json'
import enSettings from './locales/en/settings.json'
import deSettings from './locales/de/settings.json'
import enNav from './locales/en/nav.json'
import deNav from './locales/de/nav.json'
import enAuth from './locales/en/auth.json'
import deAuth from './locales/de/auth.json'
import enIssues from './locales/en/issues.json'
import deIssues from './locales/de/issues.json'
import enIssuesFields from './locales/en/issues-fields.json'
import deIssuesFields from './locales/de/issues-fields.json'
import enComments from './locales/en/comments.json'
import deComments from './locales/de/comments.json'
import enBoard from './locales/en/board.json'
import deBoard from './locales/de/board.json'
import enBacklog from './locales/en/backlog.json'
import deBacklog from './locales/de/backlog.json'
import enSprints from './locales/en/sprints.json'
import deSprints from './locales/de/sprints.json'
import enDashboard from './locales/en/dashboard.json'
import deDashboard from './locales/de/dashboard.json'
import enReports from './locales/en/reports.json'
import deReports from './locales/de/reports.json'
import enNotifications from './locales/en/notifications.json'
import deNotifications from './locales/de/notifications.json'
import enProjects from './locales/en/projects.json'
import deProjects from './locales/de/projects.json'

export const SUPPORTED_LANGUAGES = ['en', 'de'] as const
export type AppLanguage = (typeof SUPPORTED_LANGUAGES)[number]

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      en: { common: enCommon, settings: enSettings, nav: enNav, auth: enAuth, issues: enIssues, 'issues-fields': enIssuesFields, comments: enComments, board: enBoard, backlog: enBacklog, sprints: enSprints, dashboard: enDashboard, reports: enReports, notifications: enNotifications, projects: enProjects },
      de: { common: deCommon, settings: deSettings, nav: deNav, auth: deAuth, issues: deIssues, 'issues-fields': deIssuesFields, comments: deComments, board: deBoard, backlog: deBacklog, sprints: deSprints, dashboard: deDashboard, reports: deReports, notifications: deNotifications, projects: deProjects },
    },
    fallbackLng: 'en',
    supportedLngs: [...SUPPORTED_LANGUAGES],
    nonExplicitSupportedLngs: true,
    defaultNS: 'common',
    ns: ['common', 'settings', 'nav', 'auth', 'issues', 'issues-fields', 'comments', 'board', 'backlog', 'sprints', 'dashboard', 'reports', 'notifications', 'projects'],
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
