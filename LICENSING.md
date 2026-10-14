# Licensing status — provisional (not an approved license)

> **Status: DRAFT / TEAM REVIEW PENDING**  
> This file records the project's intended licensing direction. It is **not** a license grant, a legal clearance, or a statement that all repository contents are redistributable.

## Proposed direction

- **Project-authored software** (Core, future applications, Intelligence runtime, API contracts, infrastructure/scripts): propose **Apache License 2.0**, subject to confirmation of authorship/rights and approval by all relevant rights holders.
- **Team-authored explanatory documentation and synthetic data**: assess **CC BY 4.0** or Apache-2.0 depending on the asset and team decision; no approval is implied.
- **Supplied reference documents and third-party materials** (including `docs/references/`): retain their own rights/terms. Do not presume they are covered by the project's future source license.
- **Third-party dependencies, container images, model weights, datasets and any future AI providers**: retain their respective terms; inventory and applicable notices must be checked.

## Current effect

A project-wide `LICENSE` file has **not** been adopted in this branch. Merely making code accessible on GitHub does not grant a general right to reuse or redistribute it. Existing third-party licenses continue to apply to their own components.

Do **not** describe this repository as “Apache-2.0 licensed” in a README, GitHub badge, release note or competition submission until the team approves the license and the final `LICENSE`/notices are committed.

## What to decide before release

1. Confirm code/document/data authorship and obtain approval from relevant rights holders.
2. Decide the source license and exact copyright/attribution information.
3. Identify materials that must remain excluded from public source releases or retain other licenses.
4. Review dependency/model/data licensing and required notices.
5. Add the unmodified approved license text at repository root as `LICENSE`, and update this file and the release checklist.
6. Verify that public release, repository metadata and competition documents agree with the effective licensing scope.

See [License release checklist](docs/license-release-checklist.md) and [Dependency/license inventory](docs/dependency-licenses.md).

This document is preparatory project governance, **not legal advice**.
