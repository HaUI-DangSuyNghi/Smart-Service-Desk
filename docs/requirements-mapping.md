# Requirements and source review

## Documents examined

1. `docs/references/De_thi_PMNM_HaUI_2026_BAN_CHINH_THUC.pdf`: 7 pages, official HaUI brief. Text extracted; architecture diagram on p2 and mandatory-component table on p3 visually inspected.
2. `docs/references/Ke_hoach_DX_Lab_14_ngay_3_nguoi.pdf`: 13 pages, dated 06/10/2026. Architecture/contracts table on p2 visually inspected. No explicit "Ra soat V2" marker found; do not claim this is V2.
3. `docs/references/Phan_cong_DX_Lab_14_ngay_3_nguoi.xlsx`: all 8 sheets examined: `Tong_quan`, `Lich_14_ngay`, `Cong_viec`, `Moc_nghiem_thu`, `Doi_chieu_de`, `Kiem_thu`, `Rui_ro`, `Nguon`. Contains 42 work packages and 7 gates. No separate Core contracts sheet; the plan PDF p2 has provisional contracts.
4. User attachment `80ef4493-d2df-45c2-a857-259cca98ea11/Pasted text.txt`: current scope and requested acceptance criteria.
5. Original `README.md`: title only. Initial repository had no code, configuration, tests, migrations, architecture or AGENTS.md at the project root or D: parent. Three supplied PDF/XLSX files were already untracked and are preserved untouched.

Missing: `de-xuat-dx-lab-haui-smart-service-desk(1).pdf` or equivalent in the repository/current attachments. Its contents are NOT inferred as official requirements. The plan references it, which is not equivalent to reading it. No actual BTC start date/deadline provided.

## Mandatory, recommended, chosen

| Official source | Classification / requirement | Decision and component | Evidence / status |
| --- | --- | --- | --- |
| p1 section 1; p3 sections 3, 4.1 | Mandatory: Core first, 3 tiers, reusable/headless | Only tier 1 in this baseline; future layers documented | Core architecture; two process probes; no frontend dependency |
| p3 section 4.1 | Mandatory: identity/auth/authorization | Keycloak + JWT + role/workspace guards | `/me`, unit tests, negative smoke |
| p3 section 4.1 | Mandatory: gateway/API intermediary | APISIX routes, rate limit; backend still authorizes | Compose topology and gateway smoke |
| p3 section 4.1 | Mandatory: structured and/or unstructured data | PostgreSQL immutable metadata; File I/O deferred | Flyway migration; metadata round trip; File schema not full feature |
| p3 section 4.1 | Mandatory: workflow engine/state/history | Embedded Flowable, two definitions, engine history | Start/task/complete/history smoke |
| p3-4 section 4.2 | Mandatory for final product: all H-P-D-I | Establish identity/workflow/data contracts now | Human interfaces, search/dashboard/Intelligence runtime deferred; [Intelligence foundation](../intelligence/docs/architecture/overview.md) is proposed design/fixtures, not completed runtime |
| p4 section 4.3 | Mandatory for final product: one useful app | Future Smart Service Desk owns its domain | No app implemented in this scope |
| p4 section 5; p5 section 10 | Mandatory: public repo, license, release, source build, docs, CHANGELOG, issue tracker | Source/config/docs supplied; no auto-publish/tag/commit | License decision, public repo/release/issue tracker still team actions |
| p4 section 5; p5 section 7 | Mandatory: secret hygiene, core tests, RBAC | Local generated secrets ignored by Git; protected backend | `.env.example`, setup, tests/smoke |
| p5 section 8 | Mandatory: source provenance and usage rights; suitable sample data | Team-authored synthetic probe data with source/version/rights/workspace | Smoke metadata; no sensitive real data |
| p5 section 8 | Recommended: >=2 public data sources where suitable | Not necessary for this foundation | No invented two-source requirement |
| p5 sections 7,10 | Recommended: Docker/Compose, OpenAPI, 8-10-page report, 3-5-minute video | Compose and OpenAPI provided; report/video later | Do not call recommendations hard requirements |
| p5 sections 9,10 | Mandatory final demo: 10+5 minutes; slides <=12 | Preserve existing schedule | Future submission work |
| p6-7 section 14 | Roadmap: N1-2 analysis, N3-4 Core, N5-14 later phases | Reassign all 3 to Core for N1-4 | `core-team-plan.md`; relative days only |
| User attachment sections 3,5,6 | Requested baseline: two distinct reusable processes, File/Audit contract, negative tests | Two BPMNs; scope table; real engine; File contract; audit writes | `scripts/smoke.ps1`, architecture/status |

Stack names, 4 business roles, 2 workspaces, 6 ticket states, 80 tickets, 20 documents and 100 evaluation cases are team choices from the old plan, not official minimums. Baseline Core roles differ from future app roles intentionally.

## Corrections to old plan

- PDF pp1,3-4 and workbook early tasks allocate nearly all Core to BE while FE starts screens and AI starts triage. New N1-4 plan allocates Identity/Gateway/Compose to FE and Data/File/Audit to AI.
- Reuse proof moved from old N10 to initial Core acceptance. Calling a ticket endpoint with curl alone is not reuse.
- API list on old p2 is a proposal, not existing implementation. The new OpenAPI only exposes implemented routes; planned File/Audit operations remain prose contracts.
- Separate demo baseline from production readiness and completed tier 1. No invented pass, no synthetic workflow history, no claim that schema equals File service.
- Old workbook labels all 42 packages P0; use acceptance gates and explicit deferral to prevent resource overload. Preserve original XLSX/PDF; new plan is the execution overlay, not silent modification of sources.
