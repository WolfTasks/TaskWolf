import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'

// The backend's OIDC success handler redirects here with the token pair in the URL fragment
// (#accessToken=…&refreshToken=…), which the browser never sends to a server.
function readTokens() {
  const params = new URLSearchParams(window.location.hash.slice(1))
  const accessToken = params.get('accessToken')
  const refreshToken = params.get('refreshToken')
  return accessToken && refreshToken ? { accessToken, refreshToken } : null
}

export function SsoCallbackPage() {
  const { t } = useTranslation('auth')
  const navigate = useNavigate()
  const [tokens] = useState(readTokens)

  useEffect(() => {
    // Drop the tokens from the address bar and history before leaving the page.
    window.history.replaceState(null, '', window.location.pathname)
    if (!tokens) {
      navigate('/login?sso=failed', { replace: true })
      return
    }
    localStorage.setItem('accessToken', tokens.accessToken)
    localStorage.setItem('refreshToken', tokens.refreshToken)
    navigate('/', { replace: true })
  }, [tokens, navigate])

  return <p className="text-sm text-gray-400 text-center">{t('login.ssoCompleting')}</p>
}
