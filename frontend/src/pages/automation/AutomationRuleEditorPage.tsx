import { useParams, useNavigate } from 'react-router-dom'
import { useCreateRule } from '../../hooks/useAutomation'
import { RuleEditor } from '../../components/automation/RuleEditor'
import type { TriggerType, RuleConditionGroup, RuleAction } from '../../types'
import { useTranslation } from 'react-i18next'

export function AutomationRuleEditorPage() {
  const { t } = useTranslation('automation')
  const { key } = useParams<{ key: string }>()
  const navigate = useNavigate()
  const createRule = useCreateRule(key!)

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
