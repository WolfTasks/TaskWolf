// frontend/src/components/issue/CustomFieldInput.tsx
import type { CustomFieldDefinition, CustomFieldValue } from '@/types'
import { useTranslation } from 'react-i18next'

interface Props {
  definition: CustomFieldDefinition
  value: CustomFieldValue | undefined
  onChange: (value: string | null) => void
  disabled?: boolean
}

export function CustomFieldInput({ definition, value, onChange, disabled }: Props) {
  const { t } = useTranslation('issues-fields')
  const label = definition.required ? `${definition.name} *` : definition.name
  const inputClass = "w-full bg-gray-700 border border-gray-600 rounded px-3 py-1.5 text-sm text-white outline-none focus:border-blue-500"

  switch (definition.type) {
    case 'TEXT':
      return (
        <input
          type="text"
          className={inputClass}
          placeholder={label}
          value={value?.textValue ?? ''}
          disabled={disabled}
          onChange={e => onChange(e.target.value || null)}
          onBlur={e => onChange(e.target.value || null)}
        />
      )
    case 'NUMBER':
      return (
        <input
          type="number"
          className={inputClass}
          placeholder={label}
          value={value?.numberValue ?? ''}
          disabled={disabled}
          onChange={e => onChange(e.target.value || null)}
          onBlur={e => onChange(e.target.value || null)}
        />
      )
    case 'DATE':
      return (
        <input
          type="date"
          className={inputClass}
          value={value?.dateValue ?? ''}
          disabled={disabled}
          onChange={e => onChange(e.target.value || null)}
        />
      )
    case 'CHECKBOX':
      return (
        <input
          type="checkbox"
          className="w-4 h-4 cursor-pointer"
          checked={value?.booleanValue ?? false}
          disabled={disabled}
          onChange={e => onChange(e.target.checked ? 'true' : 'false')}
        />
      )
    case 'DROPDOWN':
      return (
        <select
          className={inputClass}
          value={value?.optionId ?? ''}
          disabled={disabled}
          onChange={e => onChange(e.target.value || null)}
        >
          <option value="">{t('customField.none')}</option>
          {definition.options?.map(opt => (
            <option key={opt.id} value={opt.id}>{opt.label}</option>
          ))}
        </select>
      )
    default:
      return null
  }
}
