// frontend/src/pages/projects/settings/CustomFieldsPage.tsx
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { DndContext, closestCenter, DragEndEvent } from '@dnd-kit/core'
import { SortableContext, verticalListSortingStrategy, arrayMove, useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import {
  useCustomFields, useCreateCustomField, useUpdateCustomField,
  useDeleteCustomField, useReorderCustomFields,
  useCreateOption, useDeleteOption
} from '@/hooks/useCustomFields'
import type { CustomFieldDefinition } from '@/types'

const FIELD_TYPES = ['TEXT', 'NUMBER', 'DATE', 'DROPDOWN', 'CHECKBOX'] as const

function SortableField({
  field,
  onEdit,
  onDelete,
}: {
  field: CustomFieldDefinition
  onEdit: () => void
  onDelete: () => void
}) {
  const { attributes, listeners, setNodeRef, transform, transition } = useSortable({ id: field.id })
  const style = { transform: CSS.Transform.toString(transform), transition }

  return (
    <div ref={setNodeRef} style={style}
      className="flex items-center gap-3 px-4 py-3 bg-gray-900 border border-gray-800 rounded-lg">
      <span {...attributes} {...listeners} className="cursor-grab text-gray-500 select-none">⠿</span>
      <span className="text-sm text-white font-medium flex-1">{field.name}</span>
      <span className="text-xs text-gray-500 bg-gray-800 px-2 py-0.5 rounded">{field.type}</span>
      {field.required && <span className="text-xs text-red-400">required</span>}
      <button onClick={onEdit} className="text-xs text-gray-400 hover:text-white px-2 py-1 rounded hover:bg-gray-700">Edit</button>
      <button onClick={onDelete} className="text-xs text-red-400 hover:text-red-300 px-2 py-1 rounded hover:bg-gray-700">Delete</button>
    </div>
  )
}

export function CustomFieldsPage() {
  const { key } = useParams<{ key: string }>()
  const { data: fields = [], isLoading } = useCustomFields(key!)
  const createField = useCreateCustomField(key!)
  const updateField = useUpdateCustomField(key!)
  const deleteField = useDeleteCustomField(key!)
  const reorderFields = useReorderCustomFields(key!)

  const [showCreate, setShowCreate] = useState(false)
  const [editingField, setEditingField] = useState<CustomFieldDefinition | null>(null)
  const [expandedField, setExpandedField] = useState<string | null>(null)
  const [newName, setNewName] = useState('')
  const [newType, setNewType] = useState<string>('TEXT')
  const [newRequired, setNewRequired] = useState(false)
  const [newOptionLabel, setNewOptionLabel] = useState<Record<string, string>>({})
  const [apiError, setApiError] = useState('')

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    if (!newName.trim()) return
    try {
      await createField.mutateAsync({ name: newName.trim(), type: newType, required: newRequired, sortOrder: fields.length })
      setShowCreate(false)
      setNewName('')
      setNewType('TEXT')
      setNewRequired(false)
      setApiError('')
    } catch {
      setApiError('A field with that name already exists.')
    }
  }

  async function handleUpdate(e: React.FormEvent) {
    e.preventDefault()
    if (!editingField || !newName.trim()) return
    try {
      await updateField.mutateAsync({ id: editingField.id, name: newName.trim(), type: editingField.type, required: newRequired, sortOrder: editingField.sortOrder })
      setEditingField(null)
      setApiError('')
    } catch {
      setApiError('A field with that name already exists.')
    }
  }

  async function handleDelete(id: string) {
    if (!confirm('Delete this field? All values will be lost.')) return
    await deleteField.mutateAsync(id)
  }

  function handleDragEnd(e: DragEndEvent) {
    const { active, over } = e
    if (!over || active.id === over.id) return
    const oldIdx = fields.findIndex(f => f.id === active.id)
    const newIdx = fields.findIndex(f => f.id === over.id)
    const reordered = arrayMove(fields, oldIdx, newIdx)
    reorderFields.mutate(reordered.map((f, i) => ({ id: f.id, sortOrder: i })))
  }

  function startEdit(field: CustomFieldDefinition) {
    setEditingField(field)
    setNewName(field.name)
    setNewRequired(field.required)
    setApiError('')
  }

  if (isLoading) return <div className="text-gray-400 p-6">Loading…</div>

  return (
    <div className="p-6 space-y-6 max-w-2xl">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Custom Fields</h1>
        {!showCreate && !editingField && (
          <button onClick={() => { setShowCreate(true); setApiError('') }}
            className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded text-sm font-medium">
            + New Field
          </button>
        )}
      </div>

      {apiError && <p className="text-sm text-red-400">{apiError}</p>}

      {showCreate && (
        <form onSubmit={handleCreate} className="flex flex-col gap-3 p-4 bg-gray-800 rounded-lg border border-gray-700">
          <div className="flex gap-2">
            <input value={newName} onChange={e => setNewName(e.target.value)} placeholder="Field name" autoFocus
              className="flex-1 bg-gray-700 border border-gray-600 rounded px-3 py-1.5 text-sm text-white outline-none focus:border-blue-500" />
            <select value={newType} onChange={e => setNewType(e.target.value)}
              className="bg-gray-700 border border-gray-600 rounded px-3 py-1.5 text-sm text-white outline-none">
              {FIELD_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
            </select>
          </div>
          <label className="flex items-center gap-2 text-sm text-gray-300 cursor-pointer">
            <input type="checkbox" checked={newRequired} onChange={e => setNewRequired(e.target.checked)} />
            Required
          </label>
          <div className="flex gap-2">
            <button type="submit" className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-1.5 rounded text-sm font-medium">Create</button>
            <button type="button" onClick={() => { setShowCreate(false); setApiError('') }} className="text-gray-400 hover:text-white px-3 py-1.5 text-sm">Cancel</button>
          </div>
        </form>
      )}

      <DndContext collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
        <SortableContext items={fields.map(f => f.id)} strategy={verticalListSortingStrategy}>
          <div className="flex flex-col gap-2">
            {fields.map(field => (
              <div key={field.id}>
                {editingField?.id === field.id ? (
                  <form onSubmit={handleUpdate} className="flex flex-col gap-3 p-4 bg-gray-800 rounded-lg border border-gray-700">
                    <input value={newName} onChange={e => setNewName(e.target.value)} autoFocus
                      className="bg-gray-700 border border-gray-600 rounded px-3 py-1.5 text-sm text-white outline-none focus:border-blue-500" />
                    <label className="flex items-center gap-2 text-sm text-gray-300 cursor-pointer">
                      <input type="checkbox" checked={newRequired} onChange={e => setNewRequired(e.target.checked)} />
                      Required
                    </label>
                    <div className="flex gap-2">
                      <button type="submit" className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-1.5 rounded text-sm font-medium">Save</button>
                      <button type="button" onClick={() => { setEditingField(null); setApiError('') }} className="text-gray-400 hover:text-white px-3 py-1.5 text-sm">Cancel</button>
                    </div>
                  </form>
                ) : (
                  <div className="flex flex-col gap-1">
                    <SortableField field={field} onEdit={() => startEdit(field)} onDelete={() => handleDelete(field.id)} />
                    {field.type === 'DROPDOWN' && (
                      <DropdownOptionsPanel
                        projectKey={key!}
                        field={field}
                        expanded={expandedField === field.id}
                        onToggle={() => setExpandedField(expandedField === field.id ? null : field.id)}
                        newOptionLabel={newOptionLabel[field.id] ?? ''}
                        onNewOptionLabelChange={label => setNewOptionLabel(prev => ({ ...prev, [field.id]: label }))}
                      />
                    )}
                  </div>
                )}
              </div>
            ))}
            {fields.length === 0 && !showCreate && (
              <p className="text-sm text-gray-500 py-8 text-center">No custom fields yet.</p>
            )}
          </div>
        </SortableContext>
      </DndContext>
    </div>
  )
}

function DropdownOptionsPanel({
  projectKey, field, expanded, onToggle, newOptionLabel, onNewOptionLabelChange
}: {
  projectKey: string
  field: CustomFieldDefinition
  expanded: boolean
  onToggle: () => void
  newOptionLabel: string
  onNewOptionLabelChange: (label: string) => void
}) {
  const createOption = useCreateOption(projectKey, field.id)
  const deleteOption = useDeleteOption(projectKey, field.id)

  async function handleAddOption(e: React.FormEvent) {
    e.preventDefault()
    if (!newOptionLabel.trim()) return
    await createOption.mutateAsync({ label: newOptionLabel.trim(), sortOrder: (field.options?.length ?? 0) })
    onNewOptionLabelChange('')
  }

  return (
    <div className="ml-6 border-l border-gray-700 pl-3">
      <button onClick={onToggle} className="text-xs text-gray-400 hover:text-white py-1">
        {expanded ? '▾ Hide options' : '▸ Options'} ({field.options?.length ?? 0})
      </button>
      {expanded && (
        <div className="flex flex-col gap-1 mt-1">
          {field.options?.map(opt => (
            <div key={opt.id} className="flex items-center gap-2 text-sm text-gray-300">
              <span className="flex-1">{opt.label}</span>
              <button onClick={() => deleteOption.mutate(opt.id)} className="text-xs text-red-400 hover:text-red-300">✕</button>
            </div>
          ))}
          <form onSubmit={handleAddOption} className="flex gap-2 mt-1">
            <input value={newOptionLabel} onChange={e => onNewOptionLabelChange(e.target.value)}
              placeholder="New option label" className="flex-1 bg-gray-700 border border-gray-600 rounded px-2 py-1 text-xs text-white outline-none focus:border-blue-500" />
            <button type="submit" className="bg-blue-600 hover:bg-blue-700 text-white px-3 py-1 rounded text-xs">Add</button>
          </form>
        </div>
      )}
    </div>
  )
}
