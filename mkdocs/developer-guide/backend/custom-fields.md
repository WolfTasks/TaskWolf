# Custom Fields

Custom fields are project-scoped field definitions that extend each issue with typed values. They support five types and are fully filterable in the issue list.

## Field Types

| Type       | Storage Column  | Filter UX               |
|------------|----------------|-------------------------|
| TEXT       | `text_value`    | Text input (ILIKE)      |
| NUMBER     | `number_value`  | Text input (exact match)|
| DATE       | `date_value`    | Date input (exact match)|
| DROPDOWN   | `option_id`     | Select from options     |
| CHECKBOX   | `boolean_value` | true / false select     |

## Database Schema (V25)

Three tables: `custom_field_definitions` (project-scoped, name, type, required, sort_order), `custom_field_options` (per DROPDOWN field, label, sort_order), `custom_field_values` (per-issue per-field, typed value columns).

`UNIQUE (issue_id, field_id)` — one value row per field per issue.
`option_id ON DELETE SET NULL` — deleting a dropdown option leaves the value row with a null option (not cascade-deleted).

## Backend Module

`com.taskowolf.customfields` — mirrors the labels and versions modules. REST base: `/api/v1/projects/{key}/custom-fields`.

Custom field values flow through `CreateIssueRequest` and `UpdateIssueRequest` as `customFieldValues: List<CustomFieldValueInput>?` (null = no change). The `IssueService` validates required fields on create and coerces string values to typed columns.

## Issue List Filtering (JPA Specification)

`IssueService.findByProject()` uses `JpaSpecificationExecutor<Issue>` and `IssueSpecification` factory methods. All filters (project, assignee, overdue, label, fix version, affects version, custom fields) are AND-combined via `Specification.and()`. Custom field filters are passed as repeated `cf=fieldId:value` query params; the controller parses them into a `Map<UUID, String>` and looks up the field type to build typed predicates.

## Frontend

- `useCustomFields(projectKey)` — React Query hook for field definitions with options
- `CustomFieldInput` — renders the correct control per type
- Settings at `/p/:key/settings/custom-fields` (Custom Fields nav link in sidebar)
- Issue create form shows all project fields; required fields block submit
- Issue detail sidebar shows all fields with click-to-edit via PATCH
- Issue list toolbar shows one filter control per field

## Out of Scope

Version lifecycle, multi-select dropdown, custom field audit trail, CSV export of custom field values.
