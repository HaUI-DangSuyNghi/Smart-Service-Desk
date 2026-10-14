# Licensing direction — Apache-2.0 (team alignment; release approval pending)

> **Status: TEAM DIRECTION CONFIRMED / FORMAL LICENSE GRANT PENDING**  
> The project team has aligned on **Apache License, Version 2.0 (SPDX: Apache-2.0)** as the intended license for original project code. This document records that direction only. It is **not** a present license grant, final rights clearance, or a statement that the entire repository can be redistributed.

## Intended licensing policy

| Asset | Intended handling | Current status |
| --- | --- | --- |
| Team-authored Core/backend, future apps/frontend, Intelligence code, infrastructure and scripts | Apache-2.0 | DIRECTION ALIGNED; release approval pending |
| Team-authored OpenAPI, schemas and integration contracts | Apache-2.0 | DIRECTION ALIGNED; release approval pending |
| Team-authored technical documentation | Prefer Apache-2.0 for consistency; any exception must be explicit | RIGHTS/RELEASE REVIEW PENDING |
| Synthetic AI fixtures and team-authored knowledge | Prefer Apache-2.0 after author and content-rights confirmation; no implicit permission | PENDING_OWNER_APPROVAL in manifests |
| Provided competition/planning PDFs, XLSX and other materials under `docs/references/` | Original rights and permissions only; exclude from public redistribution unless authorized | UNVERIFIED |
| Third-party dependencies, container images, models, weights, datasets and provider content | Their own license/terms, notices and restrictions | INVENTORY/AUDIT PENDING |

Choosing the same license family as many of our dependencies does **not** relicense third-party components or prove license compliance. A project's source license also does not override the licenses of datasets or AI model weights.

## Current effect and safeguards

There is **no root `LICENSE` file yet** on this branch. Until the relevant copyright holders approve the final grant and its scope, do not label this repository as already “Apache-2.0 licensed” on GitHub, in README badges, PRs or release submissions. Publishing code on GitHub alone does not provide a general reuse/redistribution license.

This is a **documentation-only preparation branch**, not an authorization to publish reference documents, ingest pending-rights AI fixtures into a public corpus, or release models/dependencies without their required notices.

## Finalization before release

1. Confirm authors/contributors and other relevant rights holders; verify any university or contest conditions.
2. Approve the Apache-2.0 license grant for specified team-owned assets and agree on copyright attribution.
3. Add the **unmodified official Apache License 2.0 text** to root `LICENSE`; add appropriate notices/headers where required.
4. Document the licensing scope, third-party exceptions and separately licensed assets here.
5. Resolve `PENDING_OWNER_APPROVAL` for the knowledge/ticket fixtures, and review supplied references for public redistribution.
6. Review dependency/container/model notices and SBOM; verify the repository and release artifacts before public distribution.
7. Update README, repository license metadata and releases only after approval; follow Git Flow and merge gates.

See [license release checklist](docs/license-release-checklist.md) and [dependency/license inventory](docs/dependency-licenses.md).

This is a project-governance draft, not legal advice.
