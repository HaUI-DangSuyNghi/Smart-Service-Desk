# Team Git Flow

## Branches

| Branch | Purpose | Branch from | Merge into |
| --- | --- | --- | --- |
| `main` | Published releases, reviewed documentation and scoped hotfixes | Existing branch | No feature commits directly |
| `develop` | Shared integration baseline for all three members | `main` | `release/*` when ready |
| `feature/<task-id>-<description>` | One scoped task | `develop` | `develop` |
| `refactor/<scope>-<description>` | Scoped structural change preserving behavior | Accepted task baseline | `develop` via reviewed PR |
| `release/<version>` | Stabilization, documentation and release fixes | `develop` | `main` and back into `develop` |
| `hotfix/<version>-<description>` | Urgent fix to an existing release | `main` | `main` and `develop`; active release branch too if applicable |

The initial `feature/core-foundation` bootstrap is merged locally with `--no-ff` into `develop` at the repository owner's request. It is not a release and must not be merged into `main` or tagged as one. All subsequent shared-branch changes use reviewed pull requests.

## Daily workflow

```powershell
git fetch origin --prune
git switch develop
git pull --ff-only origin develop
git switch -c feature/BE-02-workflow-transactions
# Edit only the task's owned files, then inspect and test changes.
git diff
git add <explicit-file-paths>
git diff --cached
git commit -m "feat(workflow): validate scoped process transactions"
git push -u origin feature/BE-02-workflow-transactions
```

Open a pull request from the feature branch into `develop`, never into `main`. Use a merge commit (equivalent to `git merge --no-ff`), not squash/rebase merge, to preserve the agreed branch history. Require at least one reviewer, preferably the reviewer listed in `docs/core-team-plan.md`. Delete the feature branch after merge when no one needs it; the foundation branch may remain as a handover reference.

Examples: BE `feature/BE-02-workflow-transactions`; FE `feature/FE-02-identity-gateway`; AI `feature/AI-02-data-audit`. Name branches by task, not permanent person-specific branches. Ownership is in the team plan; coordinate edits to Compose, Maven and OpenAPI.

To sync a published feature branch without rewriting teammates' history:

```powershell
git fetch origin
git switch feature/BE-02-workflow-transactions
git merge origin/develop
# Resolve conflicts, rerun relevant checks, then push normally.
git push
```

No force pushes or history rewrites on shared branches. Do not commit to `develop` or `main` directly. `git pull --ff-only` stopping is a signal to inspect divergence, not to reset or force-push.

## Pull request checks

- State task ID, scope, changed contracts/migrations, test commands/results and known limits. Do not call a mock/unit test integrated-service evidence.
- Backend/config changes: run `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start.ps1` and `scripts/smoke.ps1`. Build runs Maven tests. Run the DB consistency check in README when workflow/data behavior changes.
- Documentation-only changes: check paths/contracts and `git diff --check`; no need to rebuild unchanged services.
- Intelligence foundation changes: run `python scripts/intelligence/validate-foundation.py` and `git diff --check`. Preserve fixture exact-byte hashes and manifest provenance, update all moved links and keep review status proposed. Core acceptance utilities live in `scripts/acceptance/core/`; runtime/API claims require separate acceptance evidence.
- Review `git diff --cached` before committing. Never stage `.env`, `.local/`, real passwords/tokens, private keys, personal data, logs or build output. Add explicit paths; avoid blind `git add .`.
- Do not edit an already-applied migration; add the next migration. Core must not depend on application packages.

## Releases and hotfixes

Only the release owner creates `release/<version>` from an accepted `develop`. Freeze feature work on that branch, run full acceptance, then merge with `--no-ff` into `main`, tag the tested release commit `v<version>`, and merge the release branch back into `develop`. Push branches/tags only after approval. If tests fail, do not label the build a release.

Create a hotfix from the affected `main` release, verify it, merge with `--no-ff` into `main`, tag the new patch release, and merge back into `develop` and an active release branch if needed. The first release v0.1.0 is published. Never move its tag to include later fixes;
use a separately reviewed patch release only after owner approval.

## GitHub settings (repository admin)

Protect `main` and `develop`: require pull requests and at least one approval, block force pushes/deletion and require CI checks once an actual CI workflow is configured. Enable merge commits. Choose `develop` as the default branch if the team wants the active baseline visible by default; otherwise explicitly select `develop` when browsing/cloning for development.

These are required team settings, not a claim that protection or CI has already been configured. The initial CLI bootstrap does not change GitHub repository settings or visibility. The scoped source license and external-asset decisions are in `LICENSING.md`;
current version/provenance evidence is in `docs/publication/pof/README.md`.
Older bootstrap notes are historical, not the current release status.

## Three-tier ownership

Core contains technical mechanisms. HPDI public APIs expose reusable capabilities;
applications depend only on those public APIs. `assembly` composes the backend.
Python runtime is `intelligence/src/hdpi`; app profiles belong to
`intelligence/src/apps/<app>/intelligence` and are excluded from generic artifacts.
Frontend platform must build without applications; applications register routes
and capabilities through extensions at `frontend/src/main.ts`.

Run `python scripts/check-tier-boundaries.py`, `python scripts/test-tier-boundaries.py`
and the frontend lint/typecheck/test/build commands before integration. See
[ownership map](docs/architecture/tier-boundary-map.md) and
[validation](docs/architecture/REFACTOR_VALIDATION.md).

## Issues, review and maintenance after v0.1.0

Search [existing Issues](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/issues)
before reporting a duplicate. Use the bug template: release SHA, component,
reproduction steps, expected/actual behavior, environment and redacted evidence.
Never attach .env, tokens, real student data or private logs. For suspected secret
exposure/security flaws, contact a maintainer privately first, not a public issue.

Link the issue in a scoped PR and record exact-SHA tests versus mock/manual/CI
proof. A maintainer triages; another member reviews; close an issue only when its
acceptance criteria are verified by the linked change. Main currently requires an
approving review; never bypass/disable protection to meet a submission deadline.
No contributor time/score is inferred from commit or issue counts.

Documentation-only evidence updates may target main from a fresh docs/* branch.
Feature work still targets develop. Use selective staging, git diff --check and
local-link/input-hash checks; do not rebuild unchanged services or touch shared
Docker volumes. No automatic release/tag and no integration of optional PRs for
an evidence-only change. See [PoF evidence](docs/publication/POF_EVIDENCE.md).
