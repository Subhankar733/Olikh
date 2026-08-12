# OLIKH Development Rules

## Mandatory workflow

**VERIFY → MODIFY → DIFF → TEST → PUSH**

1. Before changing existing code, inspect the current `main` version in GitHub.
2. Inspect the exact surrounding code, related Gradle files, and relevant GitHub Actions workflow.
3. Never guess a function, brace, class scope, API, dependency, or file path when the repository can be inspected.
4. Make the smallest safe change that solves the verified problem.
5. Run `git diff --check`.
6. Run structural/syntax validation relevant to changed files.
7. Run the relevant Gradle compile, lint, unit tests, and Android smoke tests when applicable.
8. Do not commit/push until validation passes.
9. If GitHub Actions fails, inspect the actual failing job/log before proposing another fix.
10. Never repeat a failed fix without identifying why the previous fix failed.
11. Do not restore an older file over a newer `main` version unless the source commit is verified.
12. Never commit secrets, keystores, passwords, tokens, or generated credentials.

## CI expectations

The CI pipeline is the source of truth for build status.

A production change is complete only after the relevant validation, Android smoke test, signed APK build, and APK verification pass.

## Change discipline

- Prefer focused commits.
- Keep local backups and generated files out of Git.
- Preserve existing architecture unless a refactor is explicitly planned.
- For large source files, inspect the diff before committing.
