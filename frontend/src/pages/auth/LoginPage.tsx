import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { authApi } from '@/api/auth'

export function LoginPage() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    try {
      const { data } = await authApi.login(email, password)
      localStorage.setItem('accessToken', data.accessToken)
      localStorage.setItem('refreshToken', data.refreshToken)
      navigate('/')
    } catch {
      setError('Invalid email or password')
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <h2 className="text-xl font-semibold text-white">Sign in</h2>
      {error && <p className="text-red-400 text-sm">{error}</p>}
      <input
        type="email" value={email} onChange={e => setEmail(e.target.value)}
        placeholder="Email" required
        className="bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm"
      />
      <input
        type="password" value={password} onChange={e => setPassword(e.target.value)}
        placeholder="Password" required
        className="bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm"
      />
      <button type="submit"
        className="bg-blue-600 hover:bg-blue-700 text-white rounded px-4 py-2 text-sm font-medium">
        Sign in
      </button>
      <p className="text-sm text-gray-400 text-center">
        No account? <Link to="/register" className="text-blue-400 hover:underline">Register</Link>
      </p>
    </form>
  )
}
