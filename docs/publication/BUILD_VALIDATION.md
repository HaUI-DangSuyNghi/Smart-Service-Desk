# Sanitized source build validation - 2026-10-28

Scope: only `.local/public-source` in the isolated publication worktree, generated
from ef047a31abfd8e35ce9b6155e3a06c224262269d with 84 explicit exclusions.
No Docker commands, databases, shared demo processes or other-agent evidence used.
No backend features changed. These are packaging checks, NOT runtime/demo or
production/security/license acceptance.

| Check | Actual result | Evidence / limitation |
| --- | --- | --- |
| React dependency installation | PASS | npm 11.9.0, Node v24.14.0; `npm ci --ignore-scripts --cache <isolated-cache>`; 554 installed packages; install reported 6 vulnerabilities (3 low, 1 moderate, 2 high); not a security waiver |
| React source build | PASS, exit 0 | `npm run build` executes `tsc -b && vite build`; photo exclusion replaced by CSS gradient, no missing image URL |
| Java source/test compilation and application packaging | PASS, exit 0 | Java 17, Maven 3.9.9; `mvn -B -Dmaven.repo.local=<isolated-cache> -DskipTests package`; 292 application source files compiled; executable SNAPSHOT jar built; tests compiled but NOT executed |
| Actual Java dependency resolution | PASS | `mvn dependency:tree`; java-resolved-dependencies.txt plus 156 artifact POM/license rows and 162 embedded notice entries; 0 UNKNOWN declared POM licenses; metadata is not compatibility clearance |
| Initial Python wheel attempt | ENVIRONMENT BLOCKER, exit 1 | Installed Python 3.11.6 rejected required >=3.12,<3.13; no source change or requires-python bypass used |
| Correct Python toolchain provisioned privately | PASS | CPython 3.12.7 downloaded through uv into this worktree's .local; separate review312 venv/cache; no global interpreter registration requested |
| Generic Intelligence wheel | PASS, exit 0 | Python 3.12.7; `python -m build --wheel --no-isolation <candidate>/intelligence --outdir <outside-source-wheels>` with setuptools 75.1.0, wheel 0.44.0, build 1.2.2.post1 |
| Integration wheel | PASS, exit 0 | Same command/toolchain on intelligence/integrations; smart_service_cloud_integration-0.1.0 wheel |
| Install Python artifacts/dependencies | PASS, exit 0 | Install requirements.lock and both built wheels into isolated review312; no model/provider payloads or API keys required |
| Import installed Python outside source | PASS, exit 0 | From .local/outside-source-review, Python `-I`; imports hdpi and hdpi.http_service from review312 site-packages, metadata versions 0.3.0/0.1.0 |
| Candidate/ZIP check | PASS | `py -3.11 scripts/publication/prepare.py --check`; hashes, complete exact ZIP membership, exclusions, no image reference, no root license grant and no remote |
| Full source tests / Docker image builds / live stack | NOT RUN | Backend Dockerfile runs mvn verify; fixture-dependent Python tests/CI are affected by excluded data; no current runtime result claimed |
| End-to-end installation/run outside checkout | NOT CERTIFIED | Python installed import is narrower than a live workflow; frontend/JAR full installation/run remains a separate clean-machine gate |

Raw logs are private under this worktree's `.local/`: frontend-install.log,
frontend-build.log, backend-build.log, java-dependency-resolution.log,
python-build.log (initial mismatch), python-provision.log, python-tools.log,
python312-build.log, python312-integration-build.log, python-install.log and
python-outside-source.log. PowerShell redirects may encode logs as UTF-16.
None are borrowed from the demo agent, included in the public source ZIP, or
represented as unit/integration test results. Build outputs/dependency caches
remain local and are absent from the archive.

Re-run after any final license/data/source changes. Package success does not make
the candidate safe/legal to publish. Preserve current demo recovery ownership.
