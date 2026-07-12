import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { authApi } from '@/api/auth'
import { ssoApi, SsoConfigPublic } from '@/api/sso'

export function LoginPage() {
  const { t } = useTranslation('auth')
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')

  const { data: ssoConfigs = [] } = useQuery<SsoConfigPublic[]>({
    queryKey: ['sso-configs-public'],
    queryFn: ssoApi.listPublic,
    retry: false,
  })

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    try {
      const { data } = await authApi.login(email, password)
      localStorage.setItem('accessToken', data.accessToken)
      localStorage.setItem('refreshToken', data.refreshToken)
      navigate('/')
    } catch {
      setError(t('login.invalid'))
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <h2 className="text-xl font-semibold text-white">{t('login.title')}</h2>
      {error && <p className="text-red-400 text-sm">{error}</p>}
      <input
        type="email" value={email} onChange={e => setEmail(e.target.value)}
        placeholder={t('login.email')} required
        className="bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm"
      />
      <input
        type="password" value={password} onChange={e => setPassword(e.target.value)}
        placeholder={t('login.password')} required
        className="bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm"
      />
      <button type="submit"
        className="bg-blue-600 hover:bg-blue-700 text-white rounded px-4 py-2 text-sm font-medium">
        {t('login.submit')}
      </button>
      {ssoConfigs.length > 0 && (
        <div className="flex flex-col gap-2 pt-2 border-t border-gray-700">
          <p className="text-xs text-gray-400 text-center">{t('login.ssoDivider')}</p>
          {ssoConfigs.map(config => (
            <a
              key={config.id}
              href={`/login/oauth2/authorization/${config.id}`}
              className="bg-gray-700 hover:bg-gray-600 text-white rounded px-4 py-2 text-sm font-medium text-center"
            >
              {t('login.ssoButton', { name: config.name })}
            </a>
          ))}
        </div>
      )}
      <p className="text-sm text-gray-400 text-center">
        {t('login.noAccount')} <Link to="/register" className="text-blue-400 hover:underline">{t('login.registerLink')}</Link>
      </p>
    </form>
  )
}
