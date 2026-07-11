import { useRef } from 'react'
import { useAttachments, useUploadAttachment, useDeleteAttachment } from '@/hooks/useAttachments'
import { attachmentsApi } from '@/api/attachments'

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

interface Props {
  projectKey: string
  issueKey: string
  currentUserId?: string
  readOnly?: boolean
}

export function AttachmentPanel({ projectKey, issueKey, currentUserId, readOnly }: Props) {
  const { data: attachments = [], isLoading } = useAttachments(projectKey, issueKey)
  const upload = useUploadAttachment(projectKey, issueKey)
  const remove = useDeleteAttachment(projectKey, issueKey)
  const fileRef = useRef<HTMLInputElement>(null)

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) {
      upload.mutate(file)
      e.target.value = ''
    }
  }

  const handleDelete = (id: string, filename: string) => {
    if (confirm(`Delete "${filename}"?`)) {
      remove.mutate(id)
    }
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-gray-400 uppercase tracking-wide">Attachments</h3>
        {!readOnly && (
          <>
            <button
              onClick={() => fileRef.current?.click()}
              disabled={upload.isPending}
              className="px-3 py-1 text-xs bg-gray-800 hover:bg-gray-700 text-gray-300 rounded disabled:opacity-50"
            >
              {upload.isPending ? 'Uploading...' : '+ Upload'}
            </button>
            <input
              ref={fileRef}
              type="file"
              className="hidden"
              onChange={handleFileChange}
            />
          </>
        )}
      </div>

      {isLoading && <div className="text-gray-500 text-sm">Loading...</div>}

      {!isLoading && attachments.length === 0 && (
        <p className="text-gray-600 text-sm italic">No attachments</p>
      )}

      {attachments.map(attachment => (
        <div key={attachment.id} className="flex items-center justify-between bg-gray-900 rounded p-2">
          <div className="flex items-center gap-2 min-w-0">
            <span className="text-gray-400 text-sm">📎</span>
            <a
              href={attachmentsApi.downloadUrl(projectKey, issueKey, attachment.id)}
              download={attachment.filename}
              className="text-sm text-indigo-400 hover:text-indigo-300 truncate"
            >
              {attachment.filename}
            </a>
            <span className="text-xs text-gray-600 flex-shrink-0">
              {formatBytes(attachment.size)}
            </span>
          </div>
          {!readOnly && (currentUserId === attachment.uploaderId) && (
            <button
              onClick={() => handleDelete(attachment.id, attachment.filename)}
              className="text-xs text-gray-500 hover:text-red-400 ml-2 px-2 py-0.5 rounded flex-shrink-0"
            >
              ✕
            </button>
          )}
        </div>
      ))}
    </div>
  )
}
