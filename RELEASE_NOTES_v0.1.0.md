# Smart Service Desk v0.1.0

Published 2026-10-31T05:31:12Z (12:31:12 UTC+07).
Tag v0.1.0 resolves to ba82b2fc5eb36ea309563d2d7442db95b96a7608.
The existing tag and GitHub Release are not modified by post-release documentation.
See docs/publication/POF_EVIDENCE.md on main for the later evidence index.

## Completed scope
- Keycloak identity/workspace authorization, PostgreSQL files/metadata,
  Flowable workflows and transactional audit.
- Service Desk intake, review, assignment/handling, requester confirmation,
  rework, search/statistics and the reusable H-P-D-I capabilities.
- Room Request, React workspace/application screens and dashboard.
- Knowledge library, extracted preview, controlled downloads, cited chat and
  browser history; existing optional Intelligence integration source retained.
- Production dependency patches: follow-redirects 1.16.2 and Zod 4.6.6 with
  the compatible process-variable record schema.

## Install and run

In a fresh clone, install Docker Desktop with Linux containers/Compose v2:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/setup.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start.ps1 -NoAi
```

Open http://localhost:5173; use the locally generated DEMO_PASSWORD. Keycloak
uses port 8081 and the gateway 9080. Never publish generated .env/realm files or
run setup, realm resets or destructive volume commands on an existing demo/VM.
Native build toolchain: Java 17/Maven 3.9.9, Node >=22.12, Python >=3.12,<3.13.
See README for complete local installation and account details.

## Evidence and limits

Main 71345274ae20036320ab21d61bb9de0175f829c7 has exactly the Git tree of tested
develop 7b56e71ea1fe608d7d39de5b7a225abdb4c31de0 (21/21 GitHub checks PASS).
This is equivalent-source evidence, NOT a claim that main ran all 21 checks.
The final documentation PR changes no runtime source, dependency, payload or
evaluation-manifest bytes; its own checks are reported separately in the handoff.
Newer develop changes from PR #71 are not part of this main-based release.

Supported startup/acceptance is manual/no-AI. Experimental OCR, GPU/Qwen and
unaccepted cloud-provider runtime modes are deferred; their source presence is
not runtime certification. Existing fixtures are not production or reviewed-gold
benchmark certification. Production npm audit records one MODERATE fast-uri 3.1.8
via @hookform/resolvers -> ajv, zero HIGH/CRITICAL; no waiver or claim of full
production security certification. Optional full image security scanning is not
covered by green functional checks.

## Licensing and distribution

Apache-2.0 covers authorized original team-owned work, not third-party ownership.
The team leader's 2026-10-31 distribution confirmation is recorded in LICENSING.md
and docs/publication/release-distribution-status.csv (82 exact paths/hashes).
Reviewed fixture distribution approval does not change evaluation/runtime rights
gates or relicense external content. Existing third-party licenses/notices remain.

Merged PR #74 records two direct author permissions for DX-Lab documents and two
HaUI permissions reported by the leader. School written/email/link evidence and
applicable attribution/conditions still need archiving; HaUI rights are not Apache. No files
or history were removed. GitHub-generated source ZIP/tar archives include these
tracked assets and are NOT sanitized by omitting optional release attachments.
No model weights, private credentials, local logs, databases or Docker volumes
are prepared as release attachments. Automatic ZIP/tar.gz source archives at the
existing v0.1.0 tag were verified; no custom binary asset was uploaded.

## Post-release verification (2026-10-31; tag unchanged)

See docs/publication/pof/FINAL_VERIFICATION.md for clean FE/BE source-build PASS,
isolated direct-backend smoke/persistence PASS under an explicit PostgreSQL 16.6
override, and the observed released PostgreSQL image startup FAIL. Unmodified full
Compose and browser login were NOT TESTED in that run. Do not interpret the
fallback or earlier CI as a new unconditional default-install/security certificate.
