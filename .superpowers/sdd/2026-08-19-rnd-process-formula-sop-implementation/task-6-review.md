# Task 6 Review — Fix Round 1

## RED / GREEN

- RED: the formal validator accepted a plan with an external primary material at 0 kg; a focused test failed for the missing stable error code.
- GREEN: server and shared preview now emit `EXTERNAL_MATERIAL_WEIGHT_REQUIRED` when external material total is not positive.
- RED: schema test failed because READY artifact integrity and immutable ownership columns were absent.
- GREEN: V23 now records SHA-256, byte size, generated user ID and task assignee user ID; a binary tamper test verifies the dedicated integrity error.

## Review changes

- Artifact bytes receive atomic-write rollback cleanup through transaction completion synchronization, and READY downloads verify size and SHA-256 before returning content.
- The formula renderer rejects zero external input defensively and rounds from non-negative source weights, retaining a 100 kg displayed total.
- SOP production controls now identify major/step ownership and contain only production standard fields. Confirmation and measurement information remains in the trace appendix. It also contains version provenance, batch disclaimer, major description/remark and source-step-output flow identity.
- Formal revision and artifact owner checks prefer immutable `assignee_user_id`; a null legacy value only grants access when the active display-name lookup has exactly one matching user ID.

## Remaining scope notes

- Existing assignment APIs are name-shaped. This change adds durable `rnd_task.assignee_user_id` at the persistence boundary; a future assignment contract migration should accept an explicit assignee user ID rather than infer it from a display label.
