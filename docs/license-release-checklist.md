# License and redistribution release checklist

**Status: APACHE-2.0 TEAM DIRECTION CONFIRMED — final rights review and license grant pending.**

The team intends to license its original source code under **Apache License 2.0 (SPDX: Apache-2.0)**. This checklist remains a release gate, not proof of clearance or permission to redistribute every file.

## Decision register

| Topic | Direction | Gate |
| --- | --- | --- |
| Team-owned source, scripts and API contracts | **Apache-2.0 — team-aligned direction** | Formal rights-holder/license approval pending |
| Team-authored documentation | Prefer Apache-2.0 for consistency | Confirm authorship and any exceptions |
| Synthetic AI fixtures and knowledge | Prefer Apache-2.0, conditional on owner/content approval | `PENDING_OWNER_APPROVAL` |
| Original supplied PDF/XLSX in `docs/references/` | Do not assume Apache-2.0; original rights govern | Verify or exclude from public distribution |
| Third-party dependencies/images, datasets, models/weights | Their own licenses and notices | Full inventory and redistribution audit pending |

## Final license and release gate

- [x] Agree on **Apache License 2.0** as the target license for team-authored project source (**direction only**).
- [ ] Confirm copyright holders, contributor permissions, and any university/competition rights or conditions.
- [ ] Formally approve the Apache-2.0 grant and exact scope; add official unmodified text to root `LICENSE`.
- [ ] Confirm copyright notice years/names and any necessary `NOTICE` or per-file attribution.
- [ ] Document separately licensed/excluded paths in `LICENSING.md`.
- [ ] Audit `docs/references/` and exclude files without public redistribution permission.
- [ ] Resolve AI fixtures' `rightsStatus`/`usageRights` in source manifests before publishing or ingesting as authorized corpus.
- [ ] Inspect resolved Java/Python dependencies, transitive licenses, image packages and SBOM; preserve required notices.
- [ ] Evaluate licenses and terms for any adopted embedding model, LLM, weights, prompts with external material, or third-party dataset.
- [ ] Run secrets/privacy scan, clean-source build, integration/security tests and release checks.
- [ ] Update README, GitHub license indicator/badge, CHANGELOG and release notes **after the formal grant**.
- [ ] Obtain required team approval and follow protected-branch release process before merging into `main`.

## References

- `LICENSING.md`
- `docs/dependency-licenses.md`
- `docs/requirements-mapping.md`
- `intelligence/docs/governance/intelligence-dependency-candidates.md`
- `fixtures/intelligence/service-desk/knowledge/manifest.json`
- `fixtures/intelligence/service-desk/tickets/manifest.json`

**Scope:** Documentation-only proposal. No source licensing, third-party terms, data rights or release approvals are changed by this branch.
