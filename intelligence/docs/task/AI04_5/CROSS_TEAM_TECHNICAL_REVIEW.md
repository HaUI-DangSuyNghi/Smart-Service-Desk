# AI-04.5 Knowledge Enrichment — Cross-Team Technical Review

> **Project:** Smart Service Desk / DX-OS  
> **Branch reviewed:** `feature/ai04-5-knowledge-enrichment`  
> **Parent branch:** `feature/ai04-evaluation-foundation`  
> **Review perspectives:** Development Team + Data Engineer + System Developer / System Architect  
> **Review focus:** repository compatibility, strict boundaries, AI↔BE contracts, FE readiness, data quality, CI/release risks  
> **Overall status:** **ENGINEERING PASS — FINAL PHASE ACCEPTANCE STILL REQUIRES REMOTE CI + HUMAN DATA GOVERNANCE**

---

# 1. Executive Summary

AI-04.5 is structurally aligned with the DX-OS three-tier architecture and does not introduce a breaking runtime contract change.

Current assessment:

```text
Architecture                     PASS
Repository compatibility         PASS
Strict boundary                  PASS
Core compatibility               PASS
AI ↔ BE runtime contract         PASS
Service Desk OpenAPI             PASS
Existing FE contract             PASS
Knowledge data architecture      PASS
Corpus quality improvement       PASS
Experimental discipline          PASS

Stacked merge handling           REQUIRED
Remote CI                        REQUIRED
Human corpus review              REQUIRED
Human qrels                      REQUIRED
Holdout acceptance               REQUIRED

Major refactor required          NO
Breaking contract found          NO
Current FE migration required    NO
```

The branch is therefore **ready for integration review**, but AI-04.5 must not yet be declared fully complete.

---

# 2. Integration State

Current branch relationship:

```text
develop
   └── feature/ai04-evaluation-foundation
          └── feature/ai04-5-knowledge-enrichment
```

Observed state at review time:

```text
AI-04.5 vs AI-04
ahead: 13
behind: 0

AI-04.5 vs develop
ahead: 23
behind: 0
```

PR #28 is stacked on AI-04 rather than directly on `develop`.

Recommended merge sequence:

```text
AI-04 → develop
     ↓
rebase/retarget AI-04.5
     ↓
remote CI
     ↓
AI-04.5 merge
```

If AI-04 is squash-merged, recreate or rebase AI-04.5 so only AI-04.5-specific commits remain above the new `develop`.

---

# 3. Overall Evaluation Matrix

| Area | Dev Team | Data Engineer | System Dev / Architect | Status | Risk |
|---|---|---|---|---|---|
| Git integration | Acceptable stacked branch | N/A | Merge order must be controlled | ⚠️ | Medium |
| Strict 3-tier boundary | Clean | Clean ownership | Correct dependency direction | ✅ | Low |
| Core impact | None | None | Core remains domain-agnostic | ✅ | Low |
| Java BE runtime | Unchanged | None | No runtime coupling introduced | ✅ | Low |
| AI↔BE triage contract | Unchanged | None | Compatible | ✅ | Low |
| AI search contract | Unchanged | Supports future retrieval | Compatible | ✅ | Low |
| Service Desk OpenAPI | Unchanged | N/A | Stable public boundary | ✅ | Low |
| Existing FE contract | Unchanged | N/A | Stable | ✅ | Low |
| Knowledge corpus structure | Improved | Strong improvement | Correct Tier-3 ownership | ✅ | Low |
| Metadata schema | Useful | Strong | Must stay internal | ✅ | Low |
| Lexicon design | Good | Strong | App-owned correctly | ✅ | Low |
| Qrels design | Correct structure | Strong | Human governance pending | ⚠️ | Medium |
| Human annotation | Not completed | Required | Required for acceptance | ⚠️ | High |
| Holdout lock | Not completed | Required | Required for unbiased evidence | ⚠️ | High |
| Corpus experiments | Good | Strong evidence | Reproducible approach | ✅ | Low |
| Section chunking A3 | Correctly rejected | Correct discipline | Runtime remains unchanged | ✅ | Low |
| CI design | Strong | Strong data-quality jobs | Remote run missing | ⚠️ | Medium |
| Wheel isolation | Preserved | Good | Tier boundary protected | ✅ | Low |
| FE future readiness | Good | Good supporting metadata | Needs BE projection | ✅ | Low |
| Production ingestion | Not implemented | Expected later | Correctly out of scope | ⏳ | N/A |

---

# 4. Development Team Review

AI-04.5 changes are concentrated in:

```text
contracts/apps/servicedesk/intelligence/
fixtures/intelligence/service-desk/
intelligence/src/apps/servicedesk/intelligence/knowledge/
intelligence/tests/data_quality/
scripts/intelligence/
```

The current Java runtime and public application API are not modified by AI-04.5.

The following important components remain unchanged from `develop`:

```text
contracts/openapi/servicedesk.yaml
contracts/intelligence/v1/triage-request.schema.json
contracts/intelligence/v1/triage-result.schema.json
contracts/intelligence/v1/search-request.schema.json
contracts/intelligence/v1/search-result.schema.json
TicketTriageController.java
TicketTriageService.java
frontend/src/apps/servicedesk/api/tickets.ts
```

### Development assessment

| Check | Result |
|---|---|
| Existing REST route changed | No |
| Existing request shape changed | No |
| Existing response shape changed | No |
| Existing Java Service Desk runtime changed | No |
| Existing FE adapter changed | No |
| Runtime TF-IDF behavior changed | No |
| Production chunk policy changed | No |

**Verdict: PASS**

---

# 5. Data Engineering Review

## 5.1 Corpus redesign

The new corpus moves away from a small set of broad, overlapping knowledge documents toward narrower responsibilities:

```text
network-connectivity
account-authentication
software-application
hardware-power
hardware-display-peripheral
priority-impact-urgency
intake-evidence-quality
escalation-human-review
non-it-facilities-routing
credential-security-handling
```

This directly reduces:

```text
cross-cutting lexical collision
document responsibility overlap
ambiguous relevance judgments
```

**Verdict: PASS**

## 5.2 Metadata and lineage

The Service Desk knowledge metadata contract includes:

```text
artifactId
version
title
status
language
domain
primaryIntent
categories
conceptIds
positiveSignals
negativeSignals
relatedArtifacts
owner
sourceClass
rightsStatus
provenance
raw
curated
derivedFrom
```

This is suitable for:

- lineage;
- review state;
- rights governance;
- retrieval preparation;
- source authority;
- future filtering;
- reproducibility.

These fields should remain internal authoring/data contracts unless deliberately mapped into an application-owned public projection.

## 5.3 Lexicon

The controlled lexicon is preferable to uncontrolled keyword injection.

Correct ownership is:

```text
Service Desk lexicon
→ Tier 3
```

Do not move domain aliases such as account/network/software concepts into generic `hdpi`.

## 5.4 Raw / curated / projection model

The branch is consistent with a Bronze/Silver/Gold model:

```text
Bronze
raw immutable source

Silver
curated reviewed knowledge

Gold
deterministic retrieval projection
```

Gold artifacts should remain rebuildable rather than manually edited.

**Data engineering verdict: PASS, with human governance pending.**

---

# 6. Evaluation Evidence

Observed corpus experiment evidence:

| Experiment | Change | Hit@1 | Hit@3 | MRR | Recall@3 |
|---|---|---:|---:|---:|---:|
| A0 | Existing v0.1 corpus | 7/12 | 11/12 | 0.7389 | 0.8750 |
| A1 | Focused document structure | 10/12 | 11/12 | 0.8958 | 0.7917 |
| A2 | Controlled lexical enrichment | 11/12 | 12/12 | 0.9444 | 0.9583 |
| A3 | Section chunking experiment | 9/12 | 12/12 | 0.8611 | 0.9583 |

Interpretation:

```text
A1:
focused document responsibilities materially improve ranking precision

A2:
controlled lexical enrichment further improves retrieval

A3:
section-aware chunking does not outperform the active paragraph policy
```

A3 is correctly recorded as rejected rather than promoted because it is more advanced.

This is strong experiment discipline.

---

# 7. Human Annotation Governance

The new evaluation dataset includes:

```text
72 queries
10 candidate knowledge artifacts
720 relevance judgments
```

The repository correctly leaves those judgments pending.

This data must not yet be presented as:

```text
production benchmark
production accuracy
accepted holdout evidence
```

Required completion path:

```text
DEV qrels
    ↓
human review

HOLDOUT
    ↓
Reviewer A
Reviewer B
    ↓
adjudication
    ↓
lock
```

Recommended annotation-quality targets:

```text
raw agreement >= 85%
weighted Cohen's kappa >= 0.70
```

These are review-quality targets, not metrics to game.

**Status: PENDING**

---

# 8. System Architecture Review

Current ownership is correct.

## Tier 3 owns

```text
Service Desk corpus
Service Desk metadata
lexicon
qrels
queries
annotation rules
knowledge-data tooling
```

## Tier 2 owns

```text
generic retrieval
generic evaluation
graded metrics
generic chunking mechanics
```

## Tier 1 owns

```text
Identity
Workflow
Metadata
Files
Audit
```

Core remains unaware of Service Desk knowledge semantics.

The experimental `markdown-section@0.2.0` policy is explicitly authoring/experiment-only and does not replace the current runtime partition configuration.

**Architecture verdict: PASS**

---

# 9. AI ↔ Backend Contract Review

## 9.1 Triage

Unchanged:

```text
contracts/intelligence/v1/triage-request.schema.json
contracts/intelligence/v1/triage-result.schema.json
```

Backend still controls:

```text
profile
profile version
trusted workspace context
trusted actor context
```

Client-supplied context cannot grant authority.

**Verdict: PASS — no breaking change**

## 9.2 Search

Unchanged:

```text
contracts/intelligence/v1/search-request.schema.json
contracts/intelligence/v1/search-result.schema.json
```

Runtime citations still require:

```text
workspaceId
metadataId
sourceVersion
sourceChecksum
chunkId
```

Local authoring IDs such as:

```text
kb-network-connectivity-001
```

must not be exposed as trusted runtime citation IDs.

**Verdict: PASS**

---

# 10. Public Runtime Contracts vs Authoring Contracts

The repository now contains two different contract classes.

## Runtime/public integration contracts

```text
contracts/intelligence/v1/*
contracts/openapi/*
```

Used by runtime integration and application APIs.

## Tier-3 authoring/evaluation contracts

```text
contracts/apps/servicedesk/intelligence/*
```

Used for:

```text
corpus authoring
dataset governance
qrels
lexicon
evaluation
```

Recommendation:

> Explicitly document that `contracts/apps/servicedesk/intelligence/*` are **not public REST contracts** and must not be used to generate FE runtime types.

---

# 11. Frontend Readiness

Existing FE adapter:

```text
frontend/src/apps/servicedesk/api/tickets.ts
```

is unchanged.

Existing `TicketTriage` response remains compatible.

Therefore:

```text
Current FE migration required: NO
```

For future knowledge recommendations, the recommended flow is:

```text
Service Desk FE
      ↓
Service Desk BE application endpoint
      ↓
HPDI Intelligence/Search capability
      ↓
verified AuthorizedKnowledge citations
```

Do not expose:

```text
FE → Python AI runtime directly
FE → fixtures/intelligence/*
FE → qrels/lexicon/internal projection schema
```

---

# 12. Recommended Future FE Projection

Backend should own a stable application-facing response, conceptually:

```json
{
  "items": [
    {
      "metadataId": "uuid",
      "title": "Xác thực tài khoản và quyền truy cập",
      "excerpt": "Nếu tài khoản bị khóa...",
      "sourceVersion": "2"
    }
  ],
  "needsReview": false
}
```

FE may receive:

```text
title
excerpt
stable source id
review state
safe explanation
```

FE should not receive internal implementation details by default:

```text
lexiconVersion
projectionVersion
qrel grade
chunk policy
evaluation split
corpus digest
```

---

# 13. Ranking Score UX Rule

The search score is engine-specific.

It must not be treated as:

```text
probability
confidence
calibrated percentage
```

Do not render:

```text
AI confidence: 87%
```

from a TF-IDF/BM25/Dense score.

Prefer:

```text
Suggested knowledge
Needs review
Relevant source
Why this may help
```

---

# 14. Production Ingestion Gap

Current v0.2 knowledge remains draft:

```text
status = DRAFT
humanReviewed = false
rightsStatus = PENDING_OWNER_APPROVAL
```

Therefore it is not production knowledge yet.

Future path:

```text
curated corpus
    ↓
human review
    ↓
rights approval
    ↓
Core Metadata / Files ingestion
    ↓
Core-issued metadata UUID
    ↓
AuthorizedKnowledge
    ↓
runtime retrieval
```

This is intentionally out of AI-04.5 runtime scope.

---

# 15. CI Review

CI design is strong and includes:

```text
source evaluation
installed-wheel evaluation
source/wheel equivalence
knowledge-data-quality
boundary checks
corpus experiment verification
section-policy rejection verification
```

However PR #28 currently targets:

```text
feature/ai04-evaluation-foundation
```

while the workflow trigger targets PRs to:

```text
develop
```

Therefore the current branch has no remote workflow evidence yet.

Status:

```text
CI design: PASS
Remote CI evidence: PENDING
```

Remote CI is required before merge acceptance.

---

# 16. Risk Register

| ID | Risk | Severity | Probability | Recommendation |
|---|---|---:|---:|---|
| R1 | Stacked PR merges AI-04 history incorrectly | Medium | Medium | Merge AI-04 first, then rebase AI-04.5 |
| R2 | App data schemas mistaken for REST API | Medium | Medium | Mark authoring-only ownership explicitly |
| R3 | Local `kb-*` exposed as trusted citation | High | Low | Keep Core-issued UUID citation rule |
| R4 | Draft/LLM qrels accepted as gold | High | Low | Require human review |
| R5 | Holdout repeatedly tuned | High | Medium | Lock before final evaluation |
| R6 | Retrieval score displayed as confidence | Medium | Medium | Enforce FE guidance |
| R7 | Domain lexicon leaks into HPDI | High | Low | Keep lexicon Tier 3 |
| R8 | Draft corpus reaches production ingestion | High | Low | Require REVIEWED + approved rights |
| R9 | New chunking policy promoted without evidence | Medium | Low | Keep A3 rejected |
| R10 | Source/wheel/platform mismatch | Medium | Medium | Require remote Ubuntu/Windows CI |
| R11 | FE consumes internal AI schema directly | High | Low | BE application projection only |
| R12 | Legacy 12-query improvement overfits data | Medium | Medium | Complete DEV/HOLDOUT governance |

---

# 17. Blockers Before Merge

Required before merge:

- [ ] AI-04 merged first.
- [ ] AI-04.5 rebased or restacked cleanly onto updated `develop`.
- [ ] PR #28 retargeted to `develop`.
- [ ] Remote GitHub CI passes.
- [ ] Strict boundary checks remain zero debt.
- [ ] Source/wheel evidence equivalence passes.

Required before declaring AI-04.5 fully complete:

- [ ] Corpus human review completed.
- [ ] Lexicon human review completed.
- [ ] DEV qrels completed.
- [ ] HOLDOUT independently reviewed.
- [ ] Disagreements adjudicated.
- [ ] Holdout locked.
- [ ] Agreement metrics reviewed.
- [ ] Final acceptance evaluation executed.

---

# 18. Non-Blockers / Future Work

The following should not block the current engineering implementation:

```text
production knowledge ingestion
public knowledge-search endpoint
FE knowledge recommendation UI
BM25
Dense Retrieval
Hybrid Retrieval
RAG generation
LLM generation
```

These belong to later phases.

---

# 19. Cross-Team Recommendation Matrix

| Recommendation | Dev | Data | System | Priority |
|---|---:|---:|---:|---:|
| Merge AI-04 before AI-04.5 | ✓ | | ✓ | P0 |
| Rebase AI-04.5 onto develop | ✓ | | ✓ | P0 |
| Run remote Ubuntu/Windows CI | ✓ | ✓ | ✓ | P0 |
| Complete human qrels | | ✓ | ✓ | P0 |
| Lock holdout before final evaluation | | ✓ | ✓ | P0 |
| Keep app schemas internal | ✓ | ✓ | ✓ | P0 |
| Never expose `kb-*` as runtime citation | ✓ | ✓ | ✓ | P0 |
| Add future BE-owned knowledge DTO | ✓ | C | ✓ | P1 |
| Keep retrieval score away from confidence UI | ✓ | ✓ | ✓ | P1 |
| Ingest reviewed knowledge through Core Data later | ✓ | ✓ | ✓ | P1 |
| Use v0.2 as fixed data foundation for AI-05 | ✓ | ✓ | ✓ | P1 |

---

# 20. Final Role Assessment

## Development Team

```text
Code compatibility: PASS
Runtime stability: PASS
API compatibility: PASS
Testability: PASS
Merge-process risk: NEEDS CONTROL
```

## Data Engineering

```text
Corpus architecture: PASS
Metadata/lineage: PASS
Lexicon design: PASS
Evaluation design: PASS
Human governance: PENDING
Holdout readiness: PENDING
```

## System Developer / Architect

```text
Tier boundaries: PASS
Dependency direction: PASS
Core isolation: PASS
Runtime-authoring separation: PASS
AI-BE contract stability: PASS
FE future architecture: PASS
Remote acceptance evidence: PENDING
```

---

# 21. Final Verdict

**Proceed with AI-04.5. No architectural redesign is required.**

Current implementation quality is sufficient to continue integration and review.

Final phase acceptance remains conditional on:

```text
AI-04 merge
      ↓
AI-04.5 rebase
      ↓
remote CI
      ↓
human corpus/qrels review
      ↓
holdout lock
      ↓
final acceptance
      ↓
AI-04.5 DONE
      ↓
AI-05 retrieval upgrade
```

This design provides a strong data foundation for AI-05 without breaking the current Backend or Frontend contracts.
