interface Props {
  sprintName: string
  openIssueCount: number
  onConfirm: () => void
  onCancel: () => void
  loading: boolean
}

export function CompleteSprintDialog({ sprintName, openIssueCount, onConfirm, onCancel, loading }: Props) {
  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50">
      <div className="bg-gray-900 border border-gray-800 rounded-xl p-6 max-w-md w-full mx-4">
        <h2 className="text-lg font-bold text-white mb-2">Complete "{sprintName}"?</h2>
        {openIssueCount > 0 ? (
          <p className="text-sm text-gray-400 mb-6">
            <span className="text-yellow-400 font-medium">{openIssueCount} issue{openIssueCount !== 1 ? 's' : ''}</span>{' '}
            are not done and will be moved back to the backlog.
          </p>
        ) : (
          <p className="text-sm text-gray-400 mb-6">All issues are done. Great sprint!</p>
        )}
        <div className="flex gap-3 justify-end">
          <button onClick={onCancel} className="px-4 py-2 text-sm text-gray-400 hover:text-white">
            Cancel
          </button>
          <button
            onClick={onConfirm}
            disabled={loading}
            className="px-4 py-2 text-sm bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded"
          >
            {loading ? 'Completing...' : 'Complete Sprint'}
          </button>
        </div>
      </div>
    </div>
  )
}
