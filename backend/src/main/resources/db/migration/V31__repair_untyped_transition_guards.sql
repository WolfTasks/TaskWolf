-- B4: WorkflowService.updateGuards used to serialize List<TransitionGuard> without the "type" discriminator
-- (type erasure), so every move over such a transition failed with 500. Restore "type" from the shape:
-- "field" -> REQUIRED_FIELD, "roles" -> ROLE_RESTRICTION. Idempotent: elements with "type" stay as they are.
UPDATE workflow_transitions wt
SET guards = (
    SELECT jsonb_agg(
               CASE
                   WHEN e.g ? 'type' THEN e.g
                   WHEN e.g ? 'field' THEN jsonb_build_object('type', 'REQUIRED_FIELD') || e.g
                   WHEN e.g ? 'roles' THEN jsonb_build_object('type', 'ROLE_RESTRICTION') || e.g
                   ELSE e.g
               END
               ORDER BY e.ord
           )::text
    FROM jsonb_array_elements(wt.guards::jsonb) WITH ORDINALITY AS e(g, ord)
)
WHERE wt.guards IS NOT NULL
  AND jsonb_typeof(wt.guards::jsonb) = 'array'
  AND EXISTS (
      SELECT 1 FROM jsonb_array_elements(wt.guards::jsonb) AS x(g) WHERE NOT (x.g ? 'type')
  );
