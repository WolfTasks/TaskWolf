# Phase 9d — Custom Fields: Design Spec

**Date:** 2026-06-27  
**Status:** Approved

---

## Overview

Project-scoped custom fields that can be defined per project and filled in on every issue. Supports five field types: text, number, date, dropdown, and checkbox. Fields appear on the issue create form and the issue detail page sidebar. Required fields enforce values before save. Custom fields are filterable in the issue list via AND-combined JPA Specifications.

---

## Database Schema — V25

Three new tables:

```sql
CREATE TABLE custom_field_definitions (
    id         UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    type       VARCHAR(10)  NOT NULL CHECK (type IN ('TEXT','NUMBER','DATE','DROPDOWN','CHECKBOX')),
    required   BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (project_id, name)
);

CREATE TABLE custom_field_options (
    id         UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    field_id   UUID         NOT NULL REFERENCES custom_field_definitions(id) ON DELETE CASCADE,
    label      VARCHAR(100) NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    UNIQUE (field_id, label)
);

CREATE TABLE custom_field_values (
    id            UUID    NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    issue_id      UUID    NOT NULL REFERENCES issues(id)                   ON DELETE CASCADE,
    field_id      UUID    NOT NULL REFERENCES custom_field_definitions(id) ON DELETE CASCADE,
    text_value    TEXT,
    number_value  NUMERIC,
    date_value    DATE,
    boolean_value BOOLEAN,
    option_id     UUID    REFERENCES custom_field_options(id) ON DELETE SET NULL,
    UNIQUE (issue_id, field_id)
);
```

**Key decisions:**
- `UNIQUE (project_id, name)` — prevents duplicate field names per project
- `UNIQUE (issue_id, field_id)` — one value row per field per issue
- `option_id ON DELETE SET NULL` — deleting a dropdown option leaves the value row intact (nulled), no data loss
- Typed columns (`text_value`, `number_value`, `date_value`, `boolean_value`, `option_id`) — one column populated per value row depending on field type

---

## Backend

### New Module: `com.taskowolf.customfields`

```
domain/
  CustomFieldDefinition.kt   -- @Entity; type as enum FieldType
  CustomFieldOption.kt       -- @Entity; @ManyToOne(CustomFieldDefinition)
  CustomFieldValue.kt        -- @Entity; @ManyToOne(Issue, CustomFieldDefinition, CustomFieldOption?)
infrastructure/
  CustomFieldDefinitionRepository.kt
  CustomFieldOptionRepository.kt
  CustomFieldValueRepository.kt
application/
  CustomFieldService.kt
api/
  CustomFieldController.kt
  dto/
    CustomFieldDefinitionRequest.kt   -- name, type, required, sortOrder
    CustomFieldDefinitionResponse.kt  -- id, name, type, required, sortOrder, options[]
    CustomFieldOptionRequest.kt       -- label, sortOrder
    CustomFieldValueInput.kt          -- fieldId: UUID, value: String?
    CustomFieldValueResponse.kt       -- fieldId, fieldName, type, required + typed value fields
```

### REST Endpoints

Base path: `/api/v1/projects/{key}/custom-fields`

| Method   | Path                    | Description                              |
|----------|-------------------------|------------------------------------------|
| `GET`    | `/`                     | List all field definitions (with options)|
| `POST`   | `/`                     | Create a field definition                |
| `PUT`    | `/{id}`                 | Update name / required / sortOrder       |
| `PUT`    | `/reorder`              | Bulk-update sort_order for multiple fields — body: `List<{id: UUID, sortOrder: Int}>`|
| `DELETE` | `/{id}`                 | Delete field (cascades values + options) |
| `POST`   | `/{id}/options`         | Create a dropdown option                 |
| `PUT`    | `/{id}/options/{optId}` | Rename / reorder a dropdown option       |
| `DELETE` | `/{id}/options/{optId}` | Delete a dropdown option                 |

### Issue Integration

**`CustomFieldValueInput`** (new shared DTO):
```kotlin
data class CustomFieldValueInput(
    val fieldId: UUID,
    val value: String?  // null = clear the value
)
```

**`CreateIssueRequest`** — new optional field:
```kotlin
val customFieldValues: List<CustomFieldValueInput>? = null
```

**`UpdateIssueRequest`** — new optional field (null = no change to any field):
```kotlin
val customFieldValues: List<CustomFieldValueInput>? = null
```

**`IssueResponse`** — new field (all project fields included, value fields null when unset):
```kotlin
val customFields: List<CustomFieldValueResponse>
```

**`CustomFieldValueResponse`**:
```kotlin
data class CustomFieldValueResponse(
    val fieldId: UUID,
    val fieldName: String,
    val type: String,
    val required: Boolean,
    val textValue: String?,
    val numberValue: BigDecimal?,
    val dateValue: LocalDate?,
    val booleanValue: Boolean?,
    val optionId: UUID?,
    val optionLabel: String?
)
```

**`IssueService` coercion:** values arrive as `String?`; the service coerces by field type (`toBigDecimal()`, `LocalDate.parse()`, `toBoolean()`). Invalid coercions return `400 Bad Request`.

**Required-field validation:** On create and update, `IssueService` checks that all required fields for the project have a non-null, non-blank value in the request. Missing required values → `400 Bad Request`.

**`IssueResponse` population:** `IssueService` loads all `CustomFieldDefinition` rows for the project, then left-joins `CustomFieldValue` rows for the issue. Fields with no value row appear in the response with all value fields null.

### Filter — JPA Specification

`IssueController.list()` accepts repeated `cf` query parameters:

```
GET /api/v1/projects/{key}/issues?cf=uuid1:textvalue&cf=uuid2:true
```

Each `cf` value is split on the first `:` to extract `fieldId` and `rawValue`. `IssueController` resolves the field type for each `fieldId` by calling `CustomFieldService.getFieldType(fieldId)` before building the spec — unknown field IDs are silently ignored. A `CustomFieldSpecification` is built per pair using a correlated subquery — no `@OneToMany` mapping needed on `Issue`:

```kotlin
class CustomFieldSpec(val fieldId: UUID, val rawValue: String, val fieldType: FieldType) : Specification<Issue> {
    override fun toPredicate(root: Root<Issue>, query: CriteriaQuery<*>, cb: CriteriaBuilder): Predicate {
        val sub = query.subquery(Long::class.java)
        val cfv = sub.from(CustomFieldValue::class.java)
        sub.select(cb.literal(1L)).where(
            cb.equal(cfv.get<UUID>("issueId"), root.get("id")),
            cb.equal(cfv.get<UUID>("fieldId"), fieldId),
            typedPredicate(cfv, cb)
        )
        return cb.exists(sub)
    }
}
```

Typed predicate per field type:
- `TEXT` / `NUMBER` / `DATE` → `cb.like(cb.lower(...value column cast to string...), "%${rawValue.lowercase()}%")`
- `CHECKBOX` → `cb.equal(cfv.get<Boolean>("booleanValue"), rawValue.toBoolean())`
- `DROPDOWN` → `cb.equal(cfv.get<UUID>("optionId"), UUID.fromString(rawValue))` — rawValue is the optionId UUID; the frontend sends `cf=fieldId:optionId`

All active specs are AND-combined. `IssueRepository` implements `JpaSpecificationExecutor<Issue>`.

### Tests

`CustomFieldServiceTest` covers: create field, rename field, change required, reorder, delete field, create option, rename option, delete option, required-value validation on issue create/update, name-uniqueness guard.

---

## Frontend

### Types (`types/index.ts`)

```typescript
interface CustomFieldDefinition {
  id: string
  name: string
  type: 'TEXT' | 'NUMBER' | 'DATE' | 'DROPDOWN' | 'CHECKBOX'
  required: boolean
  sortOrder: number
  options?: CustomFieldOption[]
}

interface CustomFieldOption {
  id: string
  label: string
  sortOrder: number
}

interface CustomFieldValue {
  fieldId: string
  fieldName: string
  type: string
  required: boolean
  textValue?: string
  numberValue?: number
  dateValue?: string
  booleanValue?: boolean
  optionId?: string
  optionLabel?: string
}

// Issue gains:
//   customFields: CustomFieldValue[]
```

### API + Hook

- `api/customFields.ts` — CRUD for definitions and options (values flow through the issue endpoints)
- `hooks/useCustomFields.ts` — `useCustomFields(projectKey)` returns definitions with options

### Components

**`CustomFieldInput`** — renders the correct control for each type:

| Type       | Control                        |
|------------|--------------------------------|
| `TEXT`     | `<Input type="text" />`        |
| `NUMBER`   | `<Input type="number" />`      |
| `DATE`     | `<Input type="date" />`        |
| `DROPDOWN` | `<Select>` with options list   |
| `CHECKBOX` | `<Checkbox />`                 |

Props: `definition: CustomFieldDefinition`, `value: CustomFieldValue | undefined`, `onChange(value: string | null)`. Displays required marker (`*`) when `definition.required`.

**`CustomFieldsPage`** (`pages/projects/settings/CustomFieldsPage.tsx`):
- List all field definitions with their type badge, required indicator, and options (for DROPDOWN)
- Create field: name input + type select + required toggle
- Drag & Drop reordering via `@dnd-kit` (already in project) — calls `PUT /reorder`
- Inline DROPDOWN option management (add, rename, delete, reorder options)
- Rename and delete per field

Route: `/projects/:key/settings/custom-fields`  
AppLayout nav link under Settings, after Versions.

### Issue Create Form

New "Custom Fields" section below existing fields. Required fields shown first. `CustomFieldInput` rendered per definition. Submit blocked when any required field is empty (frontend guard mirrors backend validation). Non-empty values sent as `customFieldValues[]` in `CreateIssueRequest`.

### IssueDetailPage Sidebar

New "Custom Fields" section after Affects Versions. Each field shows label + `CustomFieldInput`. PATCH triggered on blur (text/number/date) or immediate change (dropdown/checkbox). Sends only the changed field in `customFieldValues`.

### Issue List Filters

Filter controls generated dynamically from project's custom field definitions:

| Type       | Filter control                                  |
|------------|-------------------------------------------------|
| `DROPDOWN` | Dropdown `<Select>` with the field's options    |
| `CHECKBOX` | Toggle button (true / false)                    |
| `TEXT`     | Text input (debounced 300 ms)                   |
| `NUMBER`   | Text input, numeric only (debounced)            |
| `DATE`     | Text input, date format (debounced)             |

Each active filter appends a `cf=fieldId:value` query parameter. Multiple filters are AND-combined. The filter toolbar scrolls horizontally when many fields are present.

---

## Out of Scope (Deferred)

- Multi-select custom fields (e.g. multi-dropdown)
- Per-field visibility / permission rules
- Custom field value history / audit trail
- Custom field values in CSV export
- Custom field reporting / aggregation
