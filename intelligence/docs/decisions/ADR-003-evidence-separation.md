# ADR-003: Separate INPUT and retrieval evidence

Status: **PROPOSED**, records the existing v0.1 contract; BE review pending.

## Context

Classification explanations and source citations establish different facts. A
literal input substring can explain a suggestion but cannot establish Core source
identity, integrity or authorization. Local knowledge fixture IDs have no Core binding.

## Decision

[Triage](../../../contracts/intelligence/v1/triage-result.schema.json) accepts only
INPUT evidence: an exact substring of the original title/description, not normalized
text. [Search](../../../contracts/intelligence/v1/search-result.schema.json) carries
authorized Core citations with workspace, metadata UUID, source version/checksum,
derived chunk ID, finite ranking score and excerpt. Application may compose both
results for presentation. A unified envelope requires a reviewed contract revision.

## Consequences

Triage can operate independently of a corpus/index outage. No fabricated source
UUID or statistical confidence is permitted in manual expected fixtures. Adapters
must resolve authorized Core records and recheck citations; schema validation alone
cannot prove issuance or authorization. Operational `DEGRADED` requests manual
handling; authorization denial remains an adapter rejection before data access.
