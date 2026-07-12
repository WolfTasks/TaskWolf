import i18n from './index'

const locale = () => i18n.language || 'en'

export const formatDate = (d: Date | string | number) =>
  new Intl.DateTimeFormat(locale(), { dateStyle: 'medium' }).format(new Date(d))

export const formatDateTime = (d: Date | string | number) =>
  new Intl.DateTimeFormat(locale(), { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(d))

export const formatNumber = (n: number) =>
  new Intl.NumberFormat(locale()).format(n)

const RELATIVE_UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 31536000], ['month', 2592000], ['day', 86400],
  ['hour', 3600], ['minute', 60], ['second', 1],
]

export const formatRelativeTime = (d: Date | string | number) => {
  const rtf = new Intl.RelativeTimeFormat(locale(), { numeric: 'auto' })
  const diffSeconds = (new Date(d).getTime() - Date.now()) / 1000
  for (const [unit, secs] of RELATIVE_UNITS) {
    if (Math.abs(diffSeconds) >= secs || unit === 'second') {
      return rtf.format(Math.round(diffSeconds / secs), unit)
    }
  }
  return ''
}
