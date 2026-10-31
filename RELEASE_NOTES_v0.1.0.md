# Smart Service Desk v0.1.0

DRAFT - not published; no release date assigned. Base source:
1a1b24c77d358e7cf844e6ab99964e7a221c08c2. Final tag must target the approved
main merge commit, not this draft or an uncommitted checkout.

## Scope
- Core identity/workspace permissions, workflow, metadata/files and audit.
- H-P-D-I services, Service Desk lifecycle and Room Request.
- React UI, Knowledge library/preview/download controls, cited chat/history and
  existing Intelligence integration source remain present.
- Supported startup baseline: manual/no-AI workflow (`scripts/start.ps1 -NoAi`).
  Qwen/GPU and cloud-provider runtime certification are not required or promised;
  their source remains available as optional integrations.

## Installation and evidence
Follow the root README in a fresh clone with Docker/Compose. Use generated local
credentials; never reset a shared realm/volume. Matching-SHA CI and isolated
Service Desk smoke evidence are recorded in the private fast-track handoff.
Final candidate PR checks must be matched to its exact committed SHA. The release
SHA remains unassigned until the approved develop-to-main merge commit exists.

## Limits and deferred work
Production dependency fixes: follow-redirects 1.16.1 to 1.16.2 (via Axios) and
Zod 3.25.76 to 4.6.6, with the process-variable record schema adapted to v4.
Local typecheck, lint, 180 unit tests, 23 Playwright tests and frontend build pass.
Production lockfile audit reports zero HIGH/CRITICAL findings, but fast-uri 3.1.8
remains MODERATE via @hookform/resolvers -> ajv; no waiver is implied.
These local results do not replace GitHub CI on the final committed SHA.

Unreviewed local changes, experimental OCR and optional PR #68 README artwork are
deferred. Do not distribute model weights, private configs/logs, user uploads or
uncleared data/reference/photo assets. GitHub source archives include tracked
files: attachment exclusions alone do not resolve their rights.

## Publication gate
Root LICENSE now grants Apache-2.0 only for authorized team-owned work following
team approval on 2026-10-31. It does not clear supplied documents, photo, fixtures,
models or other third-party content. Required notices and unresolved asset rights,
including existing public history, remain publication gates; no RELEASE GO yet.
After approval, preserve history with a develop-to-main merge commit, create an
annotated v0.1.0 tag at the approved main SHA and publish matching notes/artifacts.
The team leader authorized the scoped preparation commit, branch push and PR to
develop. No merge, tag or release publication is authorized.
