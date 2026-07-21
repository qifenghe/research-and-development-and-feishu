# Dual-mode Experiment Form Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add quick sampling and arrange-before-sampling modes to the real mobile and PC experiment form while preserving the existing draft API.

**Architecture:** Keep the backend contract unchanged and represent arrangement through the existing ordered `ExperimentProcessStep[]`. Put deterministic list operations and yield calculations in shared frontend helpers, then compose mobile and PC interfaces around the same data.

**Tech Stack:** Vue 3, TypeScript, Vant, Ant Design Vue, Vitest-compatible TypeScript helper tests, Spring Boot existing REST API.

---

### Task 1: Process step behavior

**Files:**
- Modify: `frontend/apps/mobile/src/components/ProcessStepEditor.helpers.ts`
- Create: `frontend/apps/mobile/src/components/ProcessStepEditor.helpers.test.ts`

- [ ] Add failing tests for move, copy, remove, and calculated loss rate.
- [ ] Run the helper test and confirm failure because the operations do not exist.
- [ ] Implement immutable process-step operations.
- [ ] Run the helper test and confirm all assertions pass.

### Task 2: Mobile dual-mode flow

**Files:**
- Modify: `frontend/apps/mobile/src/views/ExperimentFormView.vue`
- Modify: `frontend/apps/mobile/src/components/ProcessStepEditor.vue`

- [ ] Add the quick/arrange mode chooser before an editable new draft.
- [ ] Add an arrangement view with edit, copy, delete, move up, and move down actions.
- [ ] Add a focused current-step execution view with only input weight, output weight, and optional remark.
- [ ] Keep save-draft and notify-test behavior connected to the existing API.
- [ ] Verify read-only historical forms still render without the mode chooser.

### Task 3: PC alignment

**Files:**
- Modify: `frontend/apps/pc/src/views/rnd/ExperimentFormView.vue`

- [ ] Add mode selection for editable new drafts.
- [ ] Add ordered process actions for move, copy, edit, and delete.
- [ ] Keep the existing material table, export, attachment, and submission actions.

### Task 4: Verification

**Files:**
- Verify: `frontend/apps/mobile/src/views/ExperimentFormView.vue`
- Verify: `frontend/apps/pc/src/views/rnd/ExperimentFormView.vue`

- [ ] Run `pnpm --dir frontend typecheck` and expect exit code 0.
- [ ] Run `pnpm --dir frontend build` and expect exit code 0.
- [ ] Open the mobile experiment route and verify both modes reach the same saved process-step payload.

