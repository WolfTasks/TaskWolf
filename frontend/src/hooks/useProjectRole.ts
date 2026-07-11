import { useProject } from '@/hooks/useProjects'

export function useProjectRole(key: string) {
  const { data: project } = useProject(key)
  const myRole = project?.myRole
  return {
    myRole,
    // Admin-only affordances stay hidden until we positively know the role.
    isAdmin: myRole === 'ADMIN',
    // Write affordances stay enabled until we positively know it's VIEWER
    // (avoids flash-disabling; the backend 403 is the hard boundary).
    canWrite: myRole == null ? true : myRole !== 'VIEWER',
  }
}
