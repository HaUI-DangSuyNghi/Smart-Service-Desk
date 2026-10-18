# BND-00 recovery inventory

The functional source of truth is `develop@728cdc3`; the old architecture branch
is a design reference. Only BND-00 is implemented on this branch. Start BND-01
from develop after BND-00 merges; do not stack all remaining phases here.

The authoritative inventory is [boundary-debt.json](boundary-debt.json):
51 source entries and 35 compiled class dependency edges, reviewed against the
current source. These overlap intentionally: a source import and the resulting
compiled edge are distinct guard observations of the same architecture debt.
Every entry has an exact repository path, rule, value, occurrence count, unique
ID, accountable role, removal phase and expiry (`2026-12-18`, exclusive).

| Removal phase | Current debt / required correction |
| --- | --- |
| BND-01 | TicketRepository reads `process_scope`, `act_hi_procinst`, `act_ru_task`; historical V5 has a cross-owner FK. Use authorized observations and a forward migration. |
| BND-02 | Direct workflow imports and exposed Core process models; three BPMN resources in shared process resources; two business process keys in ApprovalProcesses. Add Process capability and preserve deployment identities. |
| BND-03 | Service Desk Java HTTP and schema-validator dependencies. Re-home transport and trusted context in H-P-D-I Intelligence. |
| BND-04 | Service Desk constants in Python runtime/evaluation/CLI; nine bundled profile artifacts. Make the runtime profile-driven and app profiles external. |
| BND-05 | Direct Core identity, audit, pagination and error/context contracts. Introduce the required Human/Data or public capability contracts and app representations. |
| BND-06 | Bootstrap/composition ownership remains to be separated; no runtime rename in BND-00. |
| BND-07 | Add second-domain reuse and standalone-Core proof, then close all remaining debt. |

## Gates

Run from the repository root (Python 3.12 and Java 17):

```sh
python scripts/check-tier-boundaries.py
python scripts/test-tier-boundaries.py
mvn -B -f backend/pom.xml -Dtest=TierBoundaryTest test
```

Source rules cover Java imports, app SQL literals/resources, cross-owner FKs in
migrations, process resource placement, Core process keys, Python AST app literals
and imports, bundled profile artifacts and existing frontend platform imports.
The SQL guard recognizes normal literal SQL, CTE aliases and schema-qualified
tables; it is not a complete SQL parser and does not evaluate dynamically built
identifiers. The migration ownership vocabulary currently includes Core,
`hpdi_*`, `servicedesk_*` and `roomrequest_*`; extend it with ownership tests when
adding another domain. Refactoring SQL through a capability remains mandatory.

The Java gate imports production bytecode, excluding tests, and checks exact
origin-class/target-class edges. It also catches fully qualified references
which do not appear in imports. Source occurrences cannot grow within an
existing exception; compiled exceptions allow only the recorded class pair.

New Application APIs must use application-owned representations. Existing
`ProcessRef`/`ProcessHistory` exposure is inventoried as Core dependencies;
OpenAPI representation migration follows BND-02. Standalone-Core and second-domain
runtime tests, generic wheel profile isolation and composition-root proof are
later-phase gates, not claimed by the BND-00 CI workflow.

CI runs both architecture jobs for every pull request into develop, on develop
pushes and manually. Existing functional workflows are unchanged except for
the requested Python module path. Gate status alone does not configure GitHub
branch protection; require these job checks in repository settings as appropriate.

## Paying debt

Remove an entry when its exact violation disappears, or decrease its occurrence
count when only some duplicates are removed. Both stale and expired entries fail
checks. Adding a new entry or extending expiry requires architecture review of
the concrete dependency, owner and removal plan; do not regenerate the baseline
as a routine way to make CI pass.

Diagnostic inventory commands do not update the baseline and do not constitute
an enforcement result:

```sh
python scripts/check-tier-boundaries.py --inventory
mvn -B -f backend/pom.xml -Dtest=TierBoundaryTest -Dboundary.inventory=true test
# Java diagnostic output: backend/target/boundary-java-inventory.json
```

Historical migrations, immutable profiles and business behavior are preserved in
this phase. The legacy refactor branch remains untouched; no remote snapshot tag
or push is required for this local recovery commit.
