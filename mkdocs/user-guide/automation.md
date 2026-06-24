# Automation

Automation rules run server-side when issue events occur. No code required.

## Rule Structure

```
WHEN  <trigger>
IF    <conditions>   (optional)
THEN  <actions>
```

## Triggers

| Trigger | Fires when |
|---|---|
| Issue Created | A new issue is created in the project |
| Status Changed | An issue transitions to a new status |
| Field Updated | A specific field value changes |
| Sprint Started | An active sprint begins |
| Sprint Completed | A sprint is closed |

## Conditions (optional)

Conditions filter which events actually fire the rule. Combine with **AND** or **OR**.

Examples: `Priority = Urgent`, `Assignee is empty`, `Label contains "backend"`

## Actions

| Action | What it does |
|---|---|
| Assign User | Sets the assignee |
| Set Status | Transitions the issue to a target status |
| Set Field | Updates any field to a fixed value |
| Add Comment | Posts a comment (supports `{{issue.summary}}` placeholders) |
| Create Sub-Issue | Creates a child issue with given type and summary |
| Send Notification | Pushes an in-app notification to a specific user or role |

## Creating a Rule

1. Open **Project Settings → Automation**
2. Click **New Rule**
3. Select a trigger
4. Optionally add condition groups
5. Add one or more actions
6. **Save** — the rule activates immediately
