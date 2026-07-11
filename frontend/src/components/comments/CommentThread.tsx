import { useState } from 'react'
import type { Comment } from '@/types'
import { useComments, useAddComment, useEditComment, useDeleteComment } from '@/hooks/useComments'

const formatTime = (iso: string) => {
  const d = new Date(iso)
  return d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

interface Props {
  projectKey: string
  issueKey: string
  currentUserId?: string
  readOnly?: boolean
}

export function CommentThread({ projectKey, issueKey, currentUserId, readOnly }: Props) {
  const { data, isLoading, fetchNextPage, hasNextPage, isFetchingNextPage } = useComments(projectKey, issueKey)
  const addComment = useAddComment(projectKey, issueKey)
  const editComment = useEditComment(projectKey, issueKey)
  const deleteComment = useDeleteComment(projectKey, issueKey)

  const [newBody, setNewBody] = useState('')
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editBody, setEditBody] = useState('')

  // Pages arrive newest-first; flatten to oldest -> newest for chat-style display.
  const comments: Comment[] = (data?.pages ?? [])
    .slice()
    .reverse()
    .flatMap(p => p.content.slice().reverse())

  const handleAdd = async () => {
    if (!newBody.trim()) return
    await addComment.mutateAsync(newBody.trim())
    setNewBody('')
  }

  const handleEdit = async (commentId: string) => {
    if (!editBody.trim()) return
    await editComment.mutateAsync({ commentId, body: editBody.trim() })
    setEditingId(null)
  }

  const handleDelete = (commentId: string) => {
    if (confirm('Delete this comment?')) {
      deleteComment.mutate(commentId)
    }
  }

  return (
    <div className="space-y-4">
      <div className="max-h-[26rem] overflow-y-auto space-y-4 pr-1">
        {isLoading ? (
          <div className="text-gray-500 text-sm">Loading comments...</div>
        ) : (
          <>
        {hasNextPage && (
          <button
            onClick={() => fetchNextPage()}
            disabled={isFetchingNextPage}
            className="text-xs text-indigo-400 hover:text-indigo-300 disabled:opacity-50"
          >
            {isFetchingNextPage ? 'Loading...' : 'Load older comments'}
          </button>
        )}

        {comments.length === 0 && (
          <p className="text-gray-600 text-sm italic">No comments yet</p>
        )}

        {comments.map((comment: Comment) => (
          <div key={comment.id} className="bg-gray-900 rounded-lg p-3">
            {editingId === comment.id ? (
              <div className="space-y-2">
                <textarea
                  className="w-full bg-gray-800 text-sm text-white rounded p-2 resize-none min-h-16 border border-gray-700 focus:outline-none focus:border-indigo-500"
                  value={editBody}
                  onChange={e => setEditBody(e.target.value)}
                  rows={3}
                />
                <div className="flex gap-2">
                  <button
                    onClick={() => handleEdit(comment.id)}
                    className="px-3 py-1 bg-indigo-600 hover:bg-indigo-500 text-white text-xs rounded"
                  >
                    Save
                  </button>
                  <button
                    onClick={() => setEditingId(null)}
                    className="px-3 py-1 bg-gray-700 hover:bg-gray-600 text-white text-xs rounded"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            ) : (
              <>
                <p className="text-sm text-gray-300 whitespace-pre-wrap">
                  {comment.deleted ? <span className="italic text-gray-600">Comment deleted</span> : comment.body}
                </p>
                <div className="flex items-center justify-between mt-2">
                  <span className="text-xs text-gray-600">
                    {formatTime(comment.createdAt)}
                    {comment.editedAt && ' (edited)'}
                  </span>
                  {!comment.deleted && currentUserId === comment.authorId && (
                    <div className="flex gap-1">
                      <button
                        onClick={() => { setEditingId(comment.id); setEditBody(comment.body ?? '') }}
                        className="text-xs text-gray-500 hover:text-indigo-400 px-2 py-0.5 rounded"
                      >
                        Edit
                      </button>
                      <button
                        onClick={() => handleDelete(comment.id)}
                        className="text-xs text-gray-500 hover:text-red-400 px-2 py-0.5 rounded"
                      >
                        Delete
                      </button>
                    </div>
                  )}
                </div>
              </>
            )}
          </div>
        ))}
          </>
        )}
      </div>

      {!readOnly && (
        <div className="mt-4 space-y-2">
          <textarea
            placeholder="Add a comment..."
            className="w-full bg-gray-900 text-sm text-white rounded-lg p-3 resize-none min-h-20 border border-gray-800 focus:outline-none focus:border-indigo-500"
            value={newBody}
            onChange={e => setNewBody(e.target.value)}
            rows={3}
          />
          <button
            onClick={handleAdd}
            disabled={!newBody.trim() || addComment.isPending}
            className="px-4 py-1.5 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white text-sm rounded"
          >
            {addComment.isPending ? 'Posting...' : 'Post Comment'}
          </button>
        </div>
      )}
    </div>
  )
}
