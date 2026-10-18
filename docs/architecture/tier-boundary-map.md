# DX-OS boundary map

The normative dependency definition is [ADR-005](ADR-005-dxos-three-tier-boundary.md).

```mermaid
flowchart TD
  Apps["T3: Service Desk and second-domain proof"] --> HPDI["T2: Human / Process / Data / Intelligence"]
  UIApps["T3: frontend apps"] --> UIPlatform["T2: frontend platform"]
  HPDI --> Core["T1: identity / workflow / metadata / files / audit"]
  HPDI --> Runtime["T2: generic Python Intelligence runtime"]
  Profiles["T3: app profiles and evaluation inputs"] -. configuration .-> Runtime
```

| Area | Baseline ownership | Target ownership |
| --- | --- | --- |
| `backend/.../core/{identity,workflow,data,audit,common}` | T1 | T1; public API only across tiers |
| `backend/.../apps/servicedesk` | T3 with direct T1 dependencies | T3 -> hpdi public APIs |
| `backend/.../hpdi` | absent | T2 capabilities and adapters |
| `resources/processes/service-desk*` | shared resource directory | apps/servicedesk/process |
| approval probe policies/BPMN | Core | sample T3 contributions |
| `resources/db/migration/V1..V5` | historical global stream | immutable historical stream |
| `intelligence/src` | Service Desk named runtime | generic hdpi_intelligence |
| historical `intelligence/profiles` | app policy bundled with runtime | apps/servicedesk/intelligence/profiles |
| `fixtures/intelligence/service-desk` | authored app evaluation | T3-owned exact-byte fixtures; stable paths |
| `frontend` | reserved, no framework | strict TypeScript platform/apps, standalone build and import guard |
| `contracts/openapi/core.yaml` | T1 wire API | stable path, T1 ownership |
| `contracts/openapi/servicedesk.yaml` | T3 wire API | stable path, T3 ownership |
| `contracts/intelligence/v1` | generic AI wire API | stable path, T2 ownership |

Milestones: ARC-00 inventory; ARC-01 guards; ARC-02 Process; ARC-03 Human;
ARC-04 Data/statistics; ARC-05 Intelligence; ARC-06 app cleanup;
ARC-07 frontend scaffold; ARC-08 second domain; ARC-09 bridge removal.
