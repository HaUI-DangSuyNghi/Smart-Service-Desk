# License and redistribution release checklist

**Status:** provisional. This is a release gate, not a statement of compliance.

## Proposed decision record

| Topic | Working proposal | Approval state |
| --- | --- | --- |
| Team-owned source and contracts | Apache-2.0 | PENDING |
| Team-authored documentation | CC BY 4.0 or Apache-2.0 | PENDING |
| Synthetic AI fixtures / knowledge | Asset-by-asset permission, possibly CC BY 4.0 | PENDING |
| Original supplied PDF/XLSX in `docs/references/` | Rights preserved; exclude from public redistribution unless allowed | UNVERIFIED |
| Third-party code, images, datasets and model weights | Their respective licenses and notices | AUDIT PENDING |

## Before approving the project license

- [ ] Confirm copyright holders, contributor permission, and any university/competition terms.
- [ ] Approve a project source license; add its official unmodified text to root `LICENSE`.
- [ ] Confirm copyright notice years/names and whether an additional `NOTICE` is necessary.
- [ ] Decide which documentation and dataset paths are under separate terms; state exclusions in `LICENSING.md`.
- [ ] Review all material in `docs/references/` for publication rights; remove from public distribution if not authorized.
- [ ] Review AI fixture manifests (`rightsStatus`, `usageRights`) and do not publish or ingest pending-rights artifacts as authorized content.
- [ ] Review Maven dependency tree, container packages and transitive licenses; preserve required notices and generate SBOM.
- [ ] Revisit inventory for newly adopted Python, FastAPI, LLM, embedding models, external APIs, datasets and weights.
- [ ] Scan for secrets and personal data; verify tests and clean source build.
- [ ] Synchronize README license section, badges, repository licensing metadata and release notes **only after approval**.
- [ ] Have repository owner and relevant contributors approve this gate before merging the final release into `main`.

## References

- `docs/dependency-licenses.md`
- `docs/requirements-mapping.md`
- `intelligence/docs/governance/intelligence-dependency-candidates.md`
- `fixtures/intelligence/service-desk/knowledge/manifest.json`
- `fixtures/intelligence/service-desk/tickets/manifest.json`

**Non-goal:** This branch neither grants a new license nor changes dependency licenses or private rights. It only provides a reviewable placeholder for future approval.
