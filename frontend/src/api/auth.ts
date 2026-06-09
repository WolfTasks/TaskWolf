import { apiClient } from './client'
import type { AuthResponse, User } from '@/types'

export const authApi = {
  register: (email: string, displayName: string, password: string) =>
    apiClient.post<AuthResponse>('/auth/register', { email, displayName, password }),
  login: (email: string, password: string) =>
    apiClient.post<AuthResponse>('/auth/login', { email, password }),
  me: () => apiClient.get<User>('/auth/me'),
}
