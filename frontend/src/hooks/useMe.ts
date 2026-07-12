import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { meApi, type NotificationPreferenceItem } from '@/api/me'

export function useUpdateProfile() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (displayName: string) => meApi.updateProfile(displayName).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['me'] }),
  })
}

export function useChangePassword() {
  return useMutation({
    mutationFn: (vars: { currentPassword: string; newPassword: string }) =>
      meApi.changePassword(vars.currentPassword, vars.newPassword).then(r => r.data),
  })
}

export function useNotificationPreferences() {
  return useQuery({
    queryKey: ['notification-preferences'],
    queryFn: () => meApi.getNotificationPreferences().then(r => r.data.preferences),
  })
}

export function useUpdateNotificationPreferences() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (preferences: NotificationPreferenceItem[]) =>
      meApi.updateNotificationPreferences(preferences).then(r => r.data.preferences),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notification-preferences'] }),
  })
}

export function useUpdateLanguage() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (language: string) => meApi.updateLanguage(language).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['me'] }),
  })
}
