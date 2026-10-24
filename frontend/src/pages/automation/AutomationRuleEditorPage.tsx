import { useParams, useNavigate, Navigate } from 'react-router'
import { useCreateRule } from '../../hooks/useAutomation'
import { RuleEditor } from '../../components/automation/RuleEditor'
import type { TriggerType, RuleConditionGroup, RuleAction } from '../../types'
import { useTranslation } from 'react-i18next'
import { useProjectRole } from '@/hooks/useProjectRole'

export function AutomationRuleEditorPage() {
  const { t } = useTranslation('automation')
  const { key } = useParams<{ key: string }>()
  const navigate = useNavigate()
  const createRule = useCreateRule(key!)
  const { myRole, isAdmin } = useProjectRole(key!)

  // Only admins may save rules; send everyone else back to the (read-only) list once the role is known.
  if (myRole && !isAdmin) return <Navigate to={`/p/${key}/automation`} replace />

  function handleSave({ name, triggerType, rootGroup, actions }: {
    name: string; triggerType: TriggerType; rootGroup: RuleConditionGroup; actions: RuleAction[]
  }) {
    createRule.mutate({
      name,
      triggerType,
      rootGroupLogic: rootGroup.logic,
      conditions: rootGroup.conditions,
      actions,
    }, { onSuccess: () => navigate(`/p/${key}/automation`) })
  }

  return (
    <div className="p-6">
      <h1 className="text-xl font-semibold text-zinc-100 mb-6">{t('newRuleTitle')}</h1>
      <RuleEditor onSave={handleSave} onCancel={() => navigate(`/p/${key}/automation`)} />
    </div>
  )
}
