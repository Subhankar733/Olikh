# OLIKH AI DEVELOPMENT AGENT RULES

## PRIMARY RULE

Follow this workflow for every feature, bug fix, refactor, or requested change:

VERIFY → PLAN → MODIFY → DIFF → VALIDATE → CI → REVIEW → PUSH

## 1. VERIFY BEFORE MODIFYING

- Inspect the current GitHub `main` branch before changing existing code.
- Inspect the exact target file and surrounding code.
- Inspect related Kotlin, Gradle, resource, manifest, and GitHub Actions files when relevant.
- Never guess a function name, class scope, brace location, API, dependency, package, file path, or architecture.
- Never restore an older file over current `main` without verifying the source commit.

## 2. PLAN

- Identify the smallest safe change.
- Preserve the existing architecture unless the user explicitly requests a refactor.
- Identify all files that must change before editing.
- Avoid unrelated changes.

## 3. MODIFY

- Make the smallest focused change that solves the verified requirement.
- Preserve existing behavior outside the requested change.
- Do not create duplicate functions, duplicate classes, duplicate imports, or duplicate resources.
- Do not introduce dependencies unless they are required and verified.
- Never commit secrets, tokens, passwords, keystores, signing credentials, or generated credentials.

## 4. DIFF

After modifying files:

- Run `git diff --check`.
- Inspect the complete diff for changed files.
- Confirm that only intended files changed.
- For large Kotlin files, inspect the exact changed regions and their surrounding class/function scope.
- Do not continue if the diff contains an unexpected change.

## 5. VALIDATION

Run validation appropriate to the change.

Required when applicable:

- Kotlin structural/syntax validation
- Gradle compilation
- Android lint
- Unit tests
- Android smoke tests
- APK verification

The GitHub CI pipeline is the source of truth for build status.

## 6. CI FAILURE RULE

If CI fails:

1. Read the actual failing job.
2. Read the actual failing step.
3. Read the actual error log.
4. Identify the root cause.
5. Verify the affected source code against current `main`.
6. Make one focused correction.
7. Re-run validation.
8. Re-run CI.

NEVER repeat a previously failed fix without identifying why it failed.

NEVER guess a fix from the workflow title alone.

## 7. COMPLETION RULE

A task is NOT complete merely because code was changed.

For production changes, completion requires:

- intended code change present
- clean diff
- relevant validation passed
- GitHub CI passed
- Android smoke test passed when applicable
- signed APK build and verification passed when applicable

If any required check fails, report the failure instead of claiming success.

## 8. GIT DISCIPLINE

- Prefer focused commits.
- Use descriptive commit messages.
- Do not commit temporary files.
- Do not commit local backups.
- Do not commit generated credentials.
- Keep `.tmp/` and other generated/local files out of Git.
- Never force-push unless explicitly authorized.

## 9. USER COMMUNICATION

When requesting a feature, the user should be able to describe the desired behavior in plain language.

The agent should handle the engineering workflow automatically whenever the available environment permits it.

Do not ask the user to manually locate code that can be inspected from the repository.

Do not ask the user to run Android/Gradle builds locally when CI is available.

When a manual action is unavoidable, provide one exact copy-pasteable command.

## 10. SAFETY

- Never modify unrelated functionality.
- Never remove working code merely to make a build pass.
- Never weaken tests or quality gates to hide a failure.
- Never disable lint, tests, smoke tests, or CI merely to obtain a green build.
- Never hide or suppress a real error without a verified reason.
