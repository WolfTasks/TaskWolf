# Module: automation

## Purpose

Executes no-code When/If/Then automation rules. `AutomationEngine` listens to all relevant domain events, evaluates the rule's condition tree against the triggering issue, and executes ordered actions synchronously on the event thread. Rules are scoped to a single project (`PROJECT`) or applied instance-wide (`SYSTEM`).

---

## Entities Owned

| Entity | Table | Key Fields |
|---|---|---|
| `AutomationRule` | `automation_rules` | `projectId` UUID? FK→projects (null for SYSTEM scope), `scope: RuleScope` (`PROJECT`/`SYSTEM`), `triggerType: TriggerType`, `triggerPayload` TEXT? (JSON key/value filter), `enabled` BOOLEAN, `createdBy` UUID FK→users |
| `RuleConditionGroup` | `rule_condition_groups` | `ruleId` UUID FK→automation_rules, `parentGroupId` UUID? self-reference (null = root group), `logic: GroupLogic` (`AND`/`OR`) |
| `RuleCondition` | `rule_conditions` | `groupId` UUID FK→rule_condition_groups, `type: ConditionType`, `operator` VARCHAR(20), `params` TEXT (JSON `{"value":"..."}`) |
| `RuleAction` | `rule_actions` | `ruleId` UUID FK→automation_rules, `position` INT (execution order), `type: ActionType`, `params` TEXT (JSON) |

`TriggerType` values: `ISSUE_CREATED`, `STATUS_CHANGED`, `PRIORITY_CHANGED`, `ASSIGNEE_CHANGED`, `COMMENT_ADDED`, `SPRINT_STARTED`, `SPRINT_COMPLETED`.

`ConditionType` values: `ISSUE_TYPE`, `PRIORITY`, `ASSIGNEE`, `STATUS`, `STORY_POINTS`, `PROJECT`.

`ActionType` values: `SET_STATUS`, `SET_ASSIGNEE`, `SET_PRIORITY`, `SEND_NOTIFICATION`, `CREATE_COMMENT`, `CREATE_SUBTASK`.

Condition operators: `IS`, `IS_NOT`, `CONTAINS`, `GT`, `LT`. `GT` and `LT` require the condition field to parse as a `Double`; non-numeric values return false.

---

## DB Schema

### `automation_rules`, `rule_condition_groups`, `rule_conditions`, `rule_actions` (V11)

```sql
CREATE TABLE automation_rules (
    id              UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id      UUID         REFERENCES projects(id) ON DELETE CASCADE,
    scope           VARCHAR(10)  NOT NULL,
    name            VARCHAR(255) NOT NULL,
    trigger_type    VARCHAR(50)  NOT NULL,
    trigger_payload TEXT,
    enabled         BOOLEAN      NOT NULL DEFAULT true,
    created_by      UUID         NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);
```

`project_id` is nullable — null means the rule has `SYSTEM` scope and fires across all projects. Index `idx_automation_rules_trigger` on `(trigger_type, scope, enabled)` covers the hot lookup path in `AutomationEngine`.

`rule_condition_groups.parent_group_id` is a self-referencing FK; the root group has `parent_group_id IS NULL`. Tree depth is unlimited but deeper trees increase synchronous evaluation cost.

`rule_actions.position` is an application-managed integer; `ActionExecutor` sorts actions ascending by `position` before execution.

---

## API Endpoints

### Project-scoped (`/api/v1/projects/{key}/automation/rules`)

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/v1/projects/{key}/automation/rules` | USER | List rules for project; paged (`page`, `size`) |
| `POST` | `/api/v1/projects/{key}/automation/rules` | ADMIN | Create rule; returns 201 with `AutomationRuleResponse` |
| `GET` | `/api/v1/projects/{key}/automation/rules/{rid}` | USER | Get single rule |
| `PUT` | `/api/v1/projects/{key}/automation/rules/{rid}` | ADMIN | Rename rule (body `{"name":"..."}`) |
| `DELETE` | `/api/v1/projects/{key}/automation/rules/{rid}` | ADMIN | Delete rule; returns 204 |
| `PATCH` | `/api/v1/projects/{key}/automation/rules/{rid}/toggle` | ADMIN | Toggle `enabled` flag |

### System-scoped (`/api/v1/admin/automation/rules`)

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/v1/admin/automation/rules` | ADMIN | List all SYSTEM-scope rules; paged |
| `POST` | `/api/v1/admin/automation/rules` | ADMIN | Create SYSTEM-scope rule; requires `SystemRole.ADMIN` |
| `DELETE` | `/api/v1/admin/automation/rules/{rid}` | ADMIN | Delete system rule; requires `SystemRole.ADMIN` |
| `PATCH` | `/api/v1/admin/automation/rules/{rid}/toggle` | ADMIN | Toggle system rule; requires `SystemRole.ADMIN` |

The project-scoped controller enforces membership (`requireMember`) for reads and project admin (`requireAdmin`) for writes. The admin controller re-checks `user.systemRole == SystemRole.ADMIN` inline — Spring Security role annotations are not used.

---

## Events Emitted

| Event | Published by | Payload |
|---|---|---|
| `AutomationFiredEvent` | `AutomationEngine.fire()` — after `actionExecutor.execute()` succeeds | `rule: AutomationRule`, `issue: Issue` |

---

## Events Consumed

| Event | Handler | Trigger fired | Extras passed in payload |
|---|---|---|---|
| `IssueCreatedEvent` | `AutomationEngine.onIssueCreated()` | `ISSUE_CREATED` | none |
| `IssueStatusChangedEvent` | `AutomationEngine.onStatusChanged()` | `STATUS_CHANGED` | `{"toStatusId":"<uuid>"}` |
| `IssueFieldChangedEvent` (field=`priority`) | `AutomationEngine.onFieldChanged()` | `PRIORITY_CHANGED` | `{"priority":"<value>"}` |
| `IssueFieldChangedEvent` (field=`assignee`) | `AutomationEngine.onFieldChanged()` | `ASSIGNEE_CHANGED` | none |
| `CommentCreatedEvent` | `AutomationEngine.onCommentCreated()` | `COMMENT_ADDED` | none |

`TriggerType.SPRINT_STARTED` and `TriggerType.SPRINT_COMPLETED` exist in the enum but have no `@EventListener` in `AutomationEngine`. Rules with these trigger types are never fired.

`triggerPayload` on a rule is a JSON map. The engine checks that every key/value in the rule's payload matches the event's payload. A null or blank `triggerPayload` matches all events.

---

## Key Files

- `backend/src/main/kotlin/com/taskowolf/automation/domain/AutomationRule.kt`
- `backend/src/main/kotlin/com/taskowolf/automation/domain/TriggerType.kt`
- `backend/src/main/kotlin/com/taskowolf/automation/domain/ActionType.kt`
- `backend/src/main/kotlin/com/taskowolf/automation/domain/ConditionType.kt`
- `backend/src/main/kotlin/com/taskowolf/automation/domain/RuleConditionGroup.kt`
- `backend/src/main/kotlin/com/taskowolf/automation/application/AutomationEngine.kt`
- `backend/src/main/kotlin/com/taskowolf/automation/application/ConditionEvaluator.kt`
- `backend/src/main/kotlin/com/taskowolf/automation/application/ActionExecutor.kt`
- `backend/src/main/kotlin/com/taskowolf/automation/application/AutomationService.kt`
- `backend/src/main/kotlin/com/taskowolf/automation/api/AutomationController.kt`
- `backend/src/main/kotlin/com/taskowolf/automation/api/AdminAutomationController.kt`
- `backend/src/main/resources/db/migration/V11__automation.sql`

---

## Extension Points

- **Add a new trigger type:** (1) Add the value to `TriggerType`. (2) Add an `@EventListener` method in `AutomationEngine` that calls `fire(TriggerType.YOUR_TYPE, issue, projectId, extraPayload)`. (3) Map any event-specific fields into the `eventPayload` map so `payloadMatches()` can filter on them.
- **Add a new action type:** (1) Add the value to `ActionType`. (2) Add a `when` branch in `ActionExecutor.execute()`. Set `dirty = true` and call `issueRepository.save(issue)` at the end if the action mutates the issue.
- **Add a new condition type:** (1) Add the value to `ConditionType`. (2) Add a `when` branch in `ConditionEvaluator.evaluateOne()` that returns the string value to compare against.
- **`SET_ASSIGNEE` is a stub:** The `ActionType.SET_ASSIGNEE` branch in `ActionExecutor` is empty with a comment "handled by caller". `UserRepository` is not injected into `ActionExecutor`. To implement it, inject `UserRepository` and set `issue.assignee`.

---

## Common Pitfalls

- `AutomationEngine` runs synchronously on the Spring event thread. Do not perform slow operations (HTTP calls, file I/O, long DB queries) inside any action type — they block the caller that published the domain event.
- AND/OR condition evaluation: `ConditionEvaluator.evaluate()` computes all condition results and all child-group results eagerly via `.map {}` before applying `.all {}` / `.any {}`. There is no short-circuit across the condition tree — every branch evaluates even if the outcome is already determined. Deep trees have linear cost in the total number of nodes.
- A `RuleConditionGroup` with no conditions and no child groups returns `true` regardless of the rule's logic. An empty root group means the rule fires on every matching event.
- `SPRINT_STARTED` and `SPRINT_COMPLETED` trigger types are dead code — no listeners are wired. Rules created with these types silently never fire.

---

## Example

The `CREATE_COMMENT` action branch from `ActionExecutor.execute()` — the shortest action that mutates state:

```kotlin
ActionType.CREATE_COMMENT -> {
    val body = params["body"]
    if (body != null) {
        commentRepository.save(
            Comment(
                issueId = issue.id,
                authorId = issue.reporter.id,
                body = body
            )
        )
    }
}
```

The comment is saved with `authorId = issue.reporter.id` (not the rule creator). No `CommentCreatedEvent` is published from inside `ActionExecutor`, so automation-created comments do not re-trigger rules that listen on `COMMENT_ADDED`.

---

## Test Patterns

- **`ConditionEvaluatorTest`** — pure unit test; constructs `RuleConditionGroup` and `RuleCondition` objects directly. Uses MockK for `AutomationRule` and `RuleConditionGroup` parent references. Verifies: AND group requires all conditions to pass; OR group passes when one condition passes; `GT` operator compares numeric values; empty group returns `true`.
- **`ActionExecutorTest`** — pure unit test with MockK; mocks `IssueRepository`, `WorkflowStatusRepository`, `NotificationService`, `CommentRepository`. Verifies: `SET_PRIORITY` updates `issue.priority` and calls `issueRepository.save()`; `SEND_NOTIFICATION` calls `notificationService.createDirect()` with `NotificationType.AUTOMATION`.
- **`AutomationEngineIntegrationTest`** — wires real `ConditionEvaluator` with a mock `ActionExecutor`. Verifies: rule fires and `actionExecutor.execute()` is called when conditions match; no execution when conditions do not match; `AutomationFiredEvent` is published on successful fire.
