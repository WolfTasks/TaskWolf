import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { organizationsApi } from '@/api/organizations'

export function OrgSwitcher() {
  const [switching, setSwitching] = useState(false)
  const [open, setOpen] = useState(false)
  const [switchError, setSwitchError] = useState<string | null>(null)

  const { data: orgs = [] } = useQuery({
    queryKey: ['orgs-mine'],
    queryFn: () => organizationsApi.listMine().then(r => r.data),
  })

  const handleSwitch = async (orgId: string) => {
    setSwitching(true)
    setSwitchError(null)
    setOpen(false)
    try {
      const { data } = await organizationsApi.switchOrg(orgId)
      localStorage.setItem('accessToken', data.accessToken)
      window.location.reload()
    } catch (err) {
      console.error('Failed to switch organization', err)
      setSwitchError('Failed to switch organization')
      setSwitching(false)
    }
  }

  if (orgs.length === 0) return null

  return (
    <div className="relative">
      {switchError && (
        <p className="text-red-400 text-xs px-3 pb-1">{switchError}</p>
      )}
      <button
        onClick={() => setOpen(o => !o)}
        disabled={switching}
        className="px-3 py-2 text-sm text-gray-300 hover:text-white hover:bg-gray-800 rounded w-full text-left disabled:opacity-50"
      >
        {switching ? 'Switching...' : 'Switch Org'}
      </button>
      {open && (
        <div className="absolute bottom-full mb-1 left-0 w-48 bg-gray-800 border border-gray-700 rounded shadow-lg z-50">
          {orgs.map(org => (
            <button
              key={org.id}
              onClick={() => handleSwitch(org.id)}
              className="block w-full text-left px-3 py-2 text-sm text-gray-200 hover:bg-gray-700 hover:text-white"
            >
              {org.name}
              <span className="text-gray-500 text-xs ml-1">({org.slug})</span>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
