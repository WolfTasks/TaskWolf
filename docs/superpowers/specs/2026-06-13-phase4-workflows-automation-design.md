# Phase 4: Workflows & Automation — Design Spec

**Date:** 2026-06-13
**Status:** Approved

## Overview

Phase 4 fügt zwei große Feature-Bereiche zu TaskWolf hinzu: einen visuellen **Workflow-Editor** (Status und Übergänge per Drag & Drop konfigurieren, Transition-Guards) und eine vollständige **Automation-Engine** (No-Code When/If/Then Regeln mit AND/OR-Bedingungsgruppen, mehreren Actions, pro Projekt und systemweit).

## Scope

| Feature | Entscheidung |
|---|---|
| Workflow-Editor | Visueller Canvas mit Drag & Drop Nodes, SVG-Pfeile für Transitions, Status CRUD |
| Transition-Guards | Required Fields (Feld muss gesetzt sein) + Role Restrictions (nur bestimmte Rollen dürfen Übergang ausführen) |
| Canvas-Positionen | Knotenkoordinaten (x/y) pro Status werden in `workflow_status_positions` gespeichert |
| Automation-Trigger | Event-basiert: ISSUE_CREATED, STATUS_CHANGED, PRIORITY_CHANGED, ASSIGNEE_CHANGED, COMMENT_ADDED, SPRINT_STARTED, SPRINT_COMPLETED |
| Automation-Bedingungen | AND/OR-Gruppen, beliebig verschachtelbar; Operatoren: IS, IS_NOT, CONTAINS, GT, LT |
| Automation-Actions | SET_STATUS, SET_ASSIGNEE, SET_PRIORITY, ADD_LABEL, REMOVE_LABEL, SEND_NOTIFICATION, CREATE_COMMENT, CREATE_SUBTASK (in konfigurierbarer Reihenfolge) |
| Automation-Scope | PROJECT (Project-Admin) + SYSTEM (System-Admin, gilt für alle Projekte) |
| Zeitbasierte Trigger | Nicht in Phase 4 — kein Scheduler |

## Architektur

**Muster:** Event-driven — `AutomationEngine` lauscht auf alle relevanten Domain Events über den bestehenden Spring `ApplicationEvent`-Bus. `issues` bleibt unwissend über `automation`.

```
Domain Event (z.B. IssueStatusChangedEvent)
    ↓
AutomationEngine (@EventListener)
    → lädt alle aktiven Regeln für dieses Projekt + alle SYSTEM-Regeln
    → filtert nach passendem trigger_type
    → ConditionEvaluator.evaluate(rootConditionGroup, issue) — rekursiv AND/OR
    → ActionExecutor.execute(rule.actions, issue) — in Reihenfolge nach position
    → publiziert AutomationFiredEvent (für Activity Log)
```

Workflow-Transition-Guards laufen synchron in `WorkflowService.validateTransition()`, das von `IssueService.update()` vor jedem Status-Wechsel aufgerufen wird. Bei Guard-Verletzung wird ein `TransitionGuardViolatedEvent` publiziert und ein `400 Bad Request` zurückgegeben.

### Betroffene Module

| Modul | Art | Änderung |
|---|---|---|
| `workflows` | Erweiterung | `WorkflowTransition.guards` (JSONB), `WorkflowService.validateTransition()`, neues `workflow_status_positions`-Table |
| `automation` | Neu | Vollständige Rule-Engine inkl. CRUD-API |

## Datenmodell

### Workflow-Erweiterungen

```sql
-- Transition-Guards als JSONB auf bestehender Tabelle
ALTER TABLE workflow_transitions ADD COLUMN guards JSONB;

-- Guard-Objekte im JSONB-Array:
-- { "type": "REQUIRED_FIELD", "field": "storyPoints" }
-- { "type": "ROLE_RESTRICTION", "roles": ["ADMIN"] }

-- Canvas-Positionen
CREATE TABLE workflow_status_positions (
    workflow_id UUID NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    status_id   UUID NOT NULL REFERENCES statuses(id) ON DELETE CASCADE,
    x           INT  NOT NULL DEFAULT 0,
    y           INT  NOT NULL DEFAULT 0,
    PRIMARY KEY (workflow_id, status_id)
);
```

### Automation-Modul

```sql
CREATE TABLE automation_rules (
    id              UUID        PRIMARY KEY,
    project_id      UUID        REFERENCES projects(id) ON DELETE CASCADE,  -- NULL wenn scope=SYSTEM
    scope           VARCHAR(10) NOT NULL CHECK (scope IN ('PROJECT', 'SYSTEM')),
    name            VARCHAR(255) NOT NULL,
    trigger_type    VARCHAR(50) NOT NULL,
    trigger_payload JSONB,      -- optionaler Pre-Filter: z.B. { "toStatusId": "..." } für STATUS_CHANGED,
                                -- { "priority": "CRITICAL" } für PRIORITY_CHANGED.
                                -- Die Engine prüft trigger_payload VOR der Condition-Auswertung.
                                -- NULL = Trigger feuert bei jedem Event dieses Typs.
    enabled         BOOLEAN     NOT NULL DEFAULT true,
    created_by      UUID        NOT NULL REFERENCES users(id),
    created_at      TIMESTAMP   NOT NULL
);

CREATE INDEX idx_automation_rules_trigger ON automation_rules (trigger_type, scope, enabled);

CREATE TABLE rule_condition_groups (
    id              UUID        PRIMARY KEY,
    rule_id         UUID        NOT NULL REFERENCES automation_rules(id) ON DELETE CASCADE,
    parent_group_id UUID        REFERENCES rule_condition_groups(id),  -- NULL = Root-Gruppe
    logic           VARCHAR(3)  NOT NULL CHECK (logic IN ('AND', 'OR'))
);

CREATE TABLE rule_conditions (
    id        UUID        PRIMARY KEY,
    group_id  UUID        NOT NULL REFERENCES rule_condition_groups(id) ON DELETE CASCADE,
    type      VARCHAR(50) NOT NULL,   -- ISSUE_TYPE | PRIORITY | ASSIGNEE | STATUS | LABEL | STORY_POINTS | PROJECT
    operator  VARCHAR(20) NOT NULL,   -- IS | IS_NOT | CONTAINS | GT | LT
    params    JSONB       NOT NULL    -- { "value": "BUG" }
);

CREATE TABLE rule_actions (
    id        UUID        PRIMARY KEY,
    rule_id   UUID        NOT NULL REFERENCES automation_rules(id) ON DELETE CASCADE,
    position  INT         NOT NULL,
    type      VARCHAR(50) NOT NULL,   -- SET_STATUS | SET_ASSIGNEE | SET_PRIORITY | ADD_LABEL
                                      -- REMOVE_LABEL | SEND_NOTIFICATION | CREATE_COMMENT | CREATE_SUBTASK
    params    JSONB       NOT NULL    -- { "statusId": "..." } / { "message": "..." }
);
```

**Flyway-Migrationen:** `V9__workflow_canvas.sql`, `V10__automation.sql`

## Backend

### `workflows`-Modul (Erweiterungen)

```kotlin
sealed class TransitionGuard
data class RequiredFieldGuard(val field: String) : TransitionGuard()
data class RoleRestrictionGuard(val roles: List<String>) : TransitionGuard()

// Neues Event
data class TransitionGuardViolatedEvent(val issue: Issue, val guard: TransitionGuard, val actor: User)
```

`WorkflowService.validateTransition(issue, toStatusId, actor)`:
1. Prüft ob eine `WorkflowTransition` von `issue.statusId` nach `toStatusId` existiert
2. Wertet alle Guards der Transition aus
3. Wirft `TransitionGuardViolationException` (→ HTTP 400) bei Verletzung

### `automation`-Modul (neu)

```
automation/
  domain/
    AutomationRule.kt
    RuleConditionGroup.kt
    RuleCondition.kt
    RuleAction.kt
    TriggerType.kt          (enum: ISSUE_CREATED, STATUS_CHANGED, PRIORITY_CHANGED,
                                   ASSIGNEE_CHANGED, COMMENT_ADDED,
                                   SPRINT_STARTED, SPRINT_COMPLETED)
    ConditionType.kt        (enum: ISSUE_TYPE, PRIORITY, ASSIGNEE, STATUS,
                                   LABEL, STORY_POINTS, PROJECT)
    ActionType.kt           (enum: SET_STATUS, SET_ASSIGNEE, SET_PRIORITY,
                                   ADD_LABEL, REMOVE_LABEL, SEND_NOTIFICATION,
                                   CREATE_COMMENT, CREATE_SUBTASK)
    events/AutomationFiredEvent.kt  -- publiziert nach jeder Regelausführung; kein Konsument in Phase 4 (reserviert für Phase 5 Activity Log)
  application/
    AutomationEngine.kt     (@EventListener für alle relevanten Domain Events)
    ConditionEvaluator.kt   (rekursive AND/OR-Auswertung der Condition Groups)
    ActionExecutor.kt       (dispatcht auf Action-Handler per ActionType)
    AutomationService.kt    (CRUD: create, update, delete, toggle, list)
  infrastructure/
    AutomationRuleRepository.kt
    RuleConditionGroupRepository.kt
    RuleConditionRepository.kt
    RuleActionRepository.kt
  api/
    AutomationController.kt
    dto/
      AutomationRuleResponse.kt
      CreateRuleRequest.kt
      UpdateRuleRequest.kt
```

**Engine-Kern:**

```kotlin
@EventListener
fun onIssueStatusChanged(event: IssueStatusChangedEvent) {
    val rules = ruleRepository.findByTriggerTypeAndEnabled(STATUS_CHANGED, true)
        .filter { it.scope == SYSTEM || it.projectId == event.issue.projectId }
    rules.forEach { rule ->
        if (triggerPayloadMatches(rule.triggerPayload, event)           // Pre-Filter
            && conditionEvaluator.evaluate(rule.rootConditionGroup, event.issue))
            actionExecutor.execute(rule.actions, event.issue)
    }
}
```

Jeder Domain Event hat einen eigenen `@EventListener`. `ConditionEvaluator` wertet Gruppen rekursiv aus: AND-Gruppe = alle Bedingungen müssen zutreffen, OR-Gruppe = mindestens eine muss zutreffen.

## API

### Workflow-Editor

```
GET    /api/v1/projects/{key}/workflow
       Response: WorkflowResponse inkl. statuses, transitions (mit guards), layout (x/y pro status)

POST   /api/v1/projects/{key}/workflow/statuses
PUT    /api/v1/projects/{key}/workflow/statuses/{sid}
DELETE /api/v1/projects/{key}/workflow/statuses/{sid}

POST   /api/v1/projects/{key}/workflow/transitions
PUT    /api/v1/projects/{key}/workflow/transitions/{tid}
DELETE /api/v1/projects/{key}/workflow/transitions/{tid}

PUT    /api/v1/projects/{key}/workflow/transitions/{tid}/guards
       Body: { "guards": [...] }

PUT    /api/v1/projects/{key}/workflow/layout
       Body: { "positions": [{ "statusId": "...", "x": 120, "y": 80 }] }
```

### Automation (pro Projekt)

```
GET    /api/v1/projects/{key}/automation/rules
POST   /api/v1/projects/{key}/automation/rules
GET    /api/v1/projects/{key}/automation/rules/{rid}
PUT    /api/v1/projects/{key}/automation/rules/{rid}
DELETE /api/v1/projects/{key}/automation/rules/{rid}
PATCH  /api/v1/projects/{key}/automation/rules/{rid}/toggle
```

### Automation (systemweit — nur SYSTEM_ADMIN)

```
GET    /api/v1/admin/automation/rules
POST   /api/v1/admin/automation/rules
GET    /api/v1/admin/automation/rules/{rid}
PUT    /api/v1/admin/automation/rules/{rid}
DELETE /api/v1/admin/automation/rules/{rid}
PATCH  /api/v1/admin/automation/rules/{rid}/toggle
```

## Frontend

### Dateistruktur

```
frontend/src/
  pages/
    settings/
      WorkflowEditorPage.tsx        -- /p/:key/settings/workflow
    automation/
      AutomationPage.tsx            -- /p/:key/automation
      AutomationRuleEditorPage.tsx  -- /p/:key/automation/new | /:rid/edit
    admin/
      AdminAutomationPage.tsx       -- /admin/automation

  components/
    workflow/
      WorkflowCanvas.tsx            -- @dnd-kit Canvas + SVG-Pfeile
      StatusNode.tsx                -- Drag-fähiger Status-Knoten (Name, Farbe, Kategorie-Badge)
      TransitionArrow.tsx           -- SVG-Pfeil, Klick öffnet Guard-Panel
      TransitionGuardPanel.tsx      -- Slide-over: Required Fields + Role Restrictions konfigurieren
    automation/
      RuleEditor.tsx                -- WHEN/IF/THEN Haupt-Komponente
      TriggerSelector.tsx           -- Event-Typ-Dropdown + optionaler Payload-Filter
      ConditionGroupBuilder.tsx     -- Rekursive AND/OR-Gruppen
      ConditionRow.tsx              -- Typ + Operator + Wert-Dropdown
      ActionList.tsx                -- Sortierbare Actions (@dnd-kit)
      ActionRow.tsx                 -- Action-Typ + Params-Felder
      RuleList.tsx                  -- Tabelle aller Regeln + Enable/Disable-Toggle

  hooks/
    useWorkflow.ts                  -- useWorkflowCanvas, useUpdateGuards, useSaveLayout
    useAutomation.ts                -- useRules, useCreateRule, useUpdateRule, useToggleRule

  api/
    workflow.ts
    automation.ts

  types/index.ts                    -- WorkflowLayout, TransitionGuard, AutomationRule,
                                    -- RuleConditionGroup, RuleCondition, RuleAction
```

### Neue Routen

```
/p/:key/settings/workflow      → WorkflowEditorPage          (🔑 project admin)
/p/:key/automation             → AutomationPage               (🔑 project admin)
/p/:key/automation/new         → AutomationRuleEditorPage
/p/:key/automation/:rid/edit   → AutomationRuleEditorPage
/admin/automation              → AdminAutomationPage          (🔑 system admin)
```

## Tests

```
backend/src/test/kotlin/com/taskowolf/
  workflows/WorkflowTransitionGuardTest.kt        -- Unit: Guard-Validierung (required field, role)
  automation/ConditionEvaluatorTest.kt            -- Unit: AND/OR-Auswertung, alle Operatoren
  automation/ActionExecutorTest.kt                -- Unit: jede Action-Typ einzeln
  automation/AutomationEngineIntegrationTest.kt   -- Integration: Event → Rule → Action End-to-End
  automation/AutomationControllerTest.kt          -- REST: CRUD + Toggle
```

## Nicht im Scope (Phase 4)

| Feature | Warum draußen |
|---|---|
| Zeitbasierte Trigger | Kein Scheduler — explizit ausgeschlossen |
| Webhook-Actions | Phase 6 (Developer Tools & Integrations) |
| Automation-Ausführungshistorie | Nice-to-have, Phase 5+ |
| Mehrere Workflows pro Projekt | Ein Workflow pro Projekt reicht für Phase 4 |
| Import/Export von Regeln | Phase 6+ |
