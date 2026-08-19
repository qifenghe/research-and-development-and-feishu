# Task 6 Report — Standard Formula and SOP Artifacts

## RED / GREEN

- RED: added `ProcessArtifactServiceTest` before the production artifact model/service existed; `mvn -q -Dtest=ProcessArtifactServiceTest test` failed at test compilation for the missing types.
- GREEN: implemented immutable-revision artifact generation, then the same focused suite passed with real `XSSFWorkbook` and `XWPFDocument` parsing.
- RED: added the artifact controller route test before mapping the endpoints; generation request resolved to no endpoint and failed.
- GREEN: added list/generate/download mappings with session identity, stable content type and attachment disposition; the controller test passed.
- RED: added role assertions for generate/read/download before permission rules existed; the role test failed as expected.
- GREEN: added narrow V23/default permission rules and a strict-auth session test; the role and real-session tests passed.

## Delivered

- `FORMULA_XLSX` and `SOP_DOCX` only generate from a formal revision scoped by both form and revision ID.
- Formula derives external materials from `ProcessRecipeService.aggregate(snapshot)`, excludes step outputs, uses numeric workbook cells, preserves a displayed 100 kg total, and embeds source revision, generator, yield and change reason.
- SOP orders major processes/minor steps, shows external inputs and intermediate handoff separately, production controls/deviation action, yield summaries, and a measurement-only trace appendix carrying tool/basis/confirmation fields.
- A row lock plus unique document-version key serializes concurrent same-type generation. Each attempt receives the next version, while old metadata and file bytes remain downloadable.
- Artifact IDs and storage keys are server-generated. Failed writes/rendering create `FAILED` metadata (with failure reason) and do not change the submitted revision. Database failure after file storage triggers best-effort storage cleanup.
- Reads and downloads are form+revision+artifact scoped; missing/corrupt bytes return a domain error and storage path validation remains in the archive abstraction.
- R&D owner/director can generate; tester/QA tester are read-only. Session tests cover owner identity, tester POST denial, and other-engineer denial.

## Verification

| Check | Result |
| --- | --- |
| `mvn -q -Dtest=ProcessArtifactServiceTest,ProcessPlanControllerTest,ProcessRevisionServiceTest,RolePermissionServiceTest,SessionAuthenticationInterceptorTest#enforcesRealSessionRolesAndFormOwnershipForFormalArtifactGenerationAndDownload,SchemaMigrationTest test` | PASS |
| direct shared `tsc`, PC `vue-tsc`, mobile `vue-tsc` | PASS |
| API contract and route checks | PASS |
| `git diff --check` | PASS |

`pnpm typecheck` attempted a registry metadata refresh in this isolated worktree and stopped before typechecking; the direct offline project compilers above are the authoritative result here.

## Self-review / follow-up

- The V23 migration is intentionally amended (not a new migration) because this feature series has not been released; it makes `storage_key` nullable for failed records and adds `failure_reason` plus exact artifact permissions.
- File generation has not been coupled to pricing; pricing remains a later task and formal revisions remain immutable after artifact failure.
