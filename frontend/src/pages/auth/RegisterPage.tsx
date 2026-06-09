import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { authApi } from '@/api/auth'

export function RegisterPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState({ email: '', displayName: '', password: '' })
  const [error, setError] = useState('')

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    try {
      const { data } = await authApi.register(form.email, form.displayName, form.password)
      localStorage.setItem('accessToken', data.accessToken)
      localStorage.setItem('refreshToken', data.refreshToken)
      navigate('/')
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } }
      setError(axiosErr.response?.data?.message ?? 'Registration failed')
    }
  }

  const set = (field: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm(prev => ({ ...prev, [field]: e.target.value }))

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <h2 className="text-xl font-semibold text-white">Create account</h2>
      {error && <p className="text-red-400 text-sm">{error}</p>}
      <input type="email" value={form.email} onChange={set('email')} placeholder="Email" required
        className="bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm" />
      <input type="text" value={form.displayName} onChange={set('displayName')} placeholder="Display name" required
        className="bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm" />
      <input type="password" value={form.password} onChange={set('password')} placeholder="Password (min 8 chars)" required minLength={8}
        className="bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm" />
      <button type="submit"
        className="bg-blue-600 hover:bg-blue-700 text-white rounded px-4 py-2 text-sm font-medium">
        Create account
      </button>
      <p className="text-sm text-gray-400 text-center">
        Already have an account? <Link to="/login" className="text-blue-400 hover:underline">Sign in</Link>
      </p>
    </form>
  )
}
