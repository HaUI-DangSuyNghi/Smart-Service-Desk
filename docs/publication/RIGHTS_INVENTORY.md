# Rights inventory - HOLD, not a license grant

Prepared 2026-10-28. Apache-2.0 direction is confirmed; formal approval is pending.
source-manifest.csv lists every baseline file, SHA-256 and exclusion reason.
INCLUDE_CONDITIONAL means candidate retention, NOT verified ownership/clearance.

| Scope | Candidate treatment | Human decision / missing evidence |
| --- | --- | --- |
| Original backend/frontend/Intelligence source, contracts, scripts, build files | Conditional retention | All relevant copyright holders/contributors approve scope; check copied code and university terms |
| Team docs/configs/text diagrams | Conditional retention | Confirm original authorship, quotations/privacy and documentation license |
| docs/references/* (three PDF/XLSX files) | Exclude | Supplied-document redistribution rights unverified; committee form also never copied from other workspace |
| frontend/public/landing/banner-phong-may.jpg | Exclude; candidate-only CSS gradient replacement | Photo origin/license/attribution and depicted-person permission if needed |
| fixtures/** except *.schema.json | Exclude | tickets/knowledge PENDING_OWNER_APPROVAL; generation rightsDecision PENDING; retrieval inherits corpus rights |
| task/**, docs/audits/**, docs/security/**, docs/implementation/evidence/** and /evidence/ directories | Exclude | Operational/privacy/security review; original evidence remains untouched |
| Other JSONL, serialized model/data, archive payloads | Exclude | Exact source, privacy, license and safe redistribution approval |
| licenses/** | Preserve verbatim | Component-specific terms only; do not imply project-wide grant |
| Blank/nonsecret .env.example templates | Retain | Final exact-byte review; never copy generated .env or realm |
| .git, runtime data, uploads, model weights, local caches, build outputs, untracked files | Never export | Candidate originates only from tracked approved baseline; original history stays private |

## Missing licenses/notices and provenance closure

- Root LICENSE is intentionally absent: do not add an Apache grant until approved.
  Final LICENSING.md must name scope/exceptions and approved copyright attribution.
  NOTICE is conditional on actual upstream obligations, not universally mandatory.
- Java direct POM and resolved report are not a complete transitive/plugin license
  audit. Review exact effective dependencies and LICENSE/NOTICE for Spring,
  Flowable, Jackson, Flyway, networknt, JDBC, tests and plugins.
- Python wheel resolution records exist for AI02/AI03 and dense Windows closure;
  collect actual bundled notices, including numerical-library subcomponents.
  Linux/Windows/Qwen/GPU locks are distinct closures, not one universal inventory.
- npm lock license fields require exact artifact verification, including Geist,
  Inter/Inter Tight fonts and Tabler/Lucide icons. Preserve required font/OFL,
  icon and MIT/ISC/Apache attribution; metadata alone is not a notice bundle.
  Review generated/copied shadcn UI source attribution as well as npm packages;
  installing the CLI does not establish ownership of every generated component.
- Container references are retained, not image layers. Any distributed image needs
  exact OS/JDK/runtime package licenses/notices and applicable source obligations.
  Temurin OpenJDK, Debian/Alpine, PostgreSQL, Keycloak, APISIX, nginx, Node, Python
  and Maven must not be covered with one Apache label. No image export prepared.
- Qwen base license and pinned provenance are retained in licenses/models. The
  supplied LabOps adapter model card declares no separate license; dataset rights
  pending. Do not infer base-model permission covers adapters/training data.
- E5 pinned model declaration is MIT per existing governance; no weights bundled.
  Check actual upstream notices before any separate weights redistribution. Other
  Tev1/Gemma/Qwen3-4B/provider integrations remain separately governed.
- CUDA EULA evidence exists, but CUDA is not OSI-licensed. Optional GPU runtime
  terms require separate review; do not claim the optional GPU bundle is all FOSS.

## Exclusion impacts - do not hide failures

Candidate Hero.tsx replaces the removed photo URL with native CSS gradient; no
missing photo request remains. No backend feature or application logic is changed.
Core Java/React build inputs, contracts and JSON schemas remain. Fixture removal
affects corpus loaders, evaluations, data-quality tests and CI workflows. Retained
historical docs may link to intentionally omitted reports/references. These are
not current test evidence and must not be represented as passing public CI/demo.
No failing test is disabled to conceal exclusions. Backend Dockerfile runs
`mvn verify`; package-with-skipped-tests is not Docker build certification.

## Secret and history scope

See scan-summary.json and secret-findings.csv: all 99 locally reachable refs at
snapshot, no fetch or original history mutation. Findings never contain secret
values. Six strong-pattern matches are negative-test literals, not confirmed live
keys; 136 assignment candidates include code references/test placeholders and
need final disposition. Pattern scanning cannot prove absence of secrets, PII or
uncleared copied text. Four binaries inventoried, not text-scanned. Private server
refs, LFS payloads, attachments and unknown secret formats remain unverified.

Historical references persist after tip deletion; current repository cannot be
made safely public just by removing files now. See HISTORY_OPTIONS.md. Inventory
and source candidate are preparation artifacts, not redistribution permission.
