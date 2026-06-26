import { useEditor, EditorContent } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import Link from '@tiptap/extension-link'
import Placeholder from '@tiptap/extension-placeholder'
import DOMPurify from 'dompurify'
import { useState, useEffect } from 'react'

interface Props {
  value: string | null
  onSave: (html: string) => void
}

const EMPTY_DOC = '<p></p>'

function sanitize(html: string | null): string {
  if (!html) return ''
  return DOMPurify.sanitize(html)
}

function normalise(html: string): string {
  return html === EMPTY_DOC ? '' : html
}

export function RichTextEditor({ value, onSave }: Props) {
  const [editing, setEditing] = useState(false)

  const editor = useEditor({
    extensions: [
      StarterKit,
      Link.configure({ openOnClick: false }),
      Placeholder.configure({ placeholder: 'Add a description…' }),
    ],
    content: sanitize(value),
    editorProps: {
      attributes: {
        class: 'min-h-24 focus:outline-none text-sm text-gray-300 [&_strong]:font-bold [&_em]:italic [&_code]:bg-gray-800 [&_code]:rounded [&_code]:px-1 [&_ul]:list-disc [&_ul]:pl-4 [&_ol]:list-decimal [&_ol]:pl-4',
      },
    },
    onBlur: ({ editor }) => {
      const html = normalise(editor.getHTML())
      const current = normalise(sanitize(value))
      if (html !== current) onSave(html)
      setEditing(false)
    },
  })

  // Sync editor content when value changes from server (e.g. after a save + refetch)
  useEffect(() => {
    if (editor && !editing) {
      const current = normalise(editor.getHTML())
      const incoming = normalise(sanitize(value))
      if (current !== incoming) editor.commands.setContent(incoming)
    }
  }, [editor, value, editing])

  if (!editing) {
    return (
      <div
        onClick={() => { setEditing(true); setTimeout(() => editor?.commands.focus('end'), 0) }}
        className="bg-gray-900 rounded-lg p-4 text-sm text-gray-300 min-h-24 cursor-pointer hover:ring-1 hover:ring-gray-700"
      >
        {value
          ? <div dangerouslySetInnerHTML={{ __html: sanitize(value) }} />
          : <span className="text-gray-600 italic">Add a description…</span>}
      </div>
    )
  }

  return (
    <div className="bg-gray-900 rounded-lg ring-1 ring-blue-500">
      {/* Toolbar */}
      <div className="flex gap-1 p-2 border-b border-gray-800">
        {[
          { label: 'B', title: 'Bold', action: () => editor?.chain().focus().toggleBold().run(), active: () => editor?.isActive('bold') },
          { label: 'I', title: 'Italic', action: () => editor?.chain().focus().toggleItalic().run(), active: () => editor?.isActive('italic') },
          { label: '<>', title: 'Code', action: () => editor?.chain().focus().toggleCode().run(), active: () => editor?.isActive('code') },
          { label: '• List', title: 'Bullet list', action: () => editor?.chain().focus().toggleBulletList().run(), active: () => editor?.isActive('bulletList') },
          { label: '1. List', title: 'Ordered list', action: () => editor?.chain().focus().toggleOrderedList().run(), active: () => editor?.isActive('orderedList') },
        ].map(btn => (
          <button
            key={btn.label}
            title={btn.title}
            onMouseDown={e => { e.preventDefault(); btn.action() }}
            className={`px-2 py-0.5 text-xs rounded ${btn.active?.() ? 'bg-gray-600 text-white' : 'text-gray-400 hover:bg-gray-800'}`}
          >
            {btn.label}
          </button>
        ))}
      </div>
      <div className="p-4">
        <EditorContent editor={editor} />
      </div>
    </div>
  )
}
