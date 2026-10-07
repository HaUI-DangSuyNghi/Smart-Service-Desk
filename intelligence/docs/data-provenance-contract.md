# AI-01 N1: Data / Provenance contract

Status: **proposed for BE / repository owner review**, not ratified. Baseline:
`c92b38733d00c77bd90b96318f8c70d7b19be5c0`, branch `feature/ai01-data-provenance`.
The owner requested no direct DX core edits before review. This document lives in
`intelligence/` as a review artifact; authoritative Core API/schema remain unchanged.

## Purpose and authority

Metadata records describe the provenance of a source artifact. Core owns the
record and authorization; future Intelligence consumes authorized Core API data.
Metadata creation does not upload a file or prove its existence/integrity.

| Field | Authority | Meaning / consumer rule |
| --- | --- | --- |
| `id` | Core | Server-generated UUID; stable provenance/citation identity. Never use title, filename or URL as identity. |
| `workspace` | Core authorization | Route workspace after signed JWT membership check; corpus partition, never accepted in body. |
| `title` | Caller | Display label, nonblank, at most 200 characters. |
| `source` | Caller | Declared origin, nonblank, at most 1000 characters; may be URL, repository path or team-authored identifier. Not automatically fetched or verified. |
| `version` | Caller | Artifact version, nonblank, at most 100 characters; not API version, no SemVer requirement. |
| `usageRights` (input), `usage_rights` (response) | Caller | Declared usage / redistribution rights, nonblank, at most 200 characters. Prefer a known SPDX identifier/expression when applicable; no N1 SPDX validator. |
| `checksum` | Caller in N1 | Exactly 64 lowercase hex characters; SHA-256 claim about exact source bytes. |
| `owner` | Core authorization | Verified JWT `sub`; never a body property. |
| `created_at` | Core / database | Server timestamp with timezone; never accepted from caller. |

Input has exactly `title`, `source`, `version`, `usageRights`, `checksum`.
Unknown properties, including `id`, `workspace`, `owner`, `created_at`, return 400.
Keep existing public response names, especially `usage_rights` and `created_at`.

Examples of source: `team-authored:core-probe`,
`repository:docs/knowledge/network-guide.md`, or an actual source URL.
Examples of artifact version: `1`, `1.0.0`, `2026-10-07`, `rev-3`.
Do not invent external origins or license grants. Record uncertain rights for
internal review; do not put unresolved artifacts in a released/public corpus.

## Immutable record and checksum trust

The N1 public API exposes create and read only. This is API-level immutability,
not a claim that administrators cannot change the database. Significant changes
to artifact content or provenance produce a new metadata record and identity.
No metadata update/list/search endpoint is introduced.

Checksum algorithm: SHA-256 over the **exact bytes** of the referenced artifact.
For a text file, hash its actual encoding/BOM/newline bytes; do not normalize first.
N1 trust is `DECLARED_BY_CALLER`: regex validation checks format only. The digest
is a fingerprint claim, not a unique record key, authorization proof, or evidence
that Core has verified a file. Repeated digests may have different metadata IDs.

N3 File I/O must compute SHA-256 from received bytes on the server, compare with
the declared claim, and only then describe that file's integrity as verified.
`VERIFIED_BY_CORE` describes that future verification event, not an existing
metadata response field or database column. No schema change is needed in N1.

## Current authorization and proposed decision

| Operation | Current baseline | Proposed policy, requires review |
| --- | --- | --- |
| POST metadata | Member + `core-writer` + `core-reader` | Member + `core-writer` |
| GET metadata | Member + `core-reader` | Same |

POST currently calls the public GET method after insert/audit, indirectly imposing
`core-reader`. A writer-only request is therefore denied and the surrounding
transaction should roll back. Do not publish writer-only success until Core code,
tests, OpenAPI and smoke agree and BE reviews the change.

Workspace sharing is distinct from ownership: any member with `core-reader` may
read metadata in that workspace; metadata GET does not require record ownership.
Missing/invalid JWT returns 401. For authenticated callers:

* No membership in route workspace: 403 before lookup.
* Missing operation role: 403 before lookup.
* Member with read role looking up a foreign/nonexistent ID within own workspace:
  404, using both workspace and ID in the query.

Example: `/lab-b` member requesting `/lab-a/metadata/{id}` gets 403; requesting
`/lab-b/metadata/{lab-a-id}` gets 404. These outcomes prevent foreign ID enumeration.

## Audit and transaction

A successful create must commit one metadata row and exactly one
`metadata.create` audit row together. Audit actor = record owner/JWT subject,
resource = metadata UUID, workspace = authorized route workspace, request ID =
Core response `X-Request-ID`; audit timestamp is server-generated. A failed create
must not leave metadata or audit rows. HTTP alone cannot prove audit atomicity;
use the read-only DB consistency checks and eventual Core regression tests.

## Core / Intelligence boundary

Core owns identity, workspace, metadata, source/version/rights/checksum, canonical
files when implemented, authorization and audit. AI ownership of the N1 work
package does not transfer ownership of those capabilities to Intelligence.

Future Intelligence owns normalized text, chunks, indexes/vectors/cache, model
outputs and evaluation. Its state must be rebuildable from authoritative Core
data plus versioned AI configuration. It must use authorized API context and
reference `workspace`, `metadata.id`, artifact `version` and `checksum` in its
derived index/evidence. Citation identity must include the authorized workspace.
Version labels alone are not globally unique or ordered.

Deleting an Intelligence index must leave Core data valid. No direct Core DB
consumption by an Intelligence runtime; no embedding, model, prompt, category,
confidence or chunk fields in Core metadata. This N1 work adds no AI runtime.

## Handover and gate

See the [acceptance report](AI_N1_ACCEPTANCE_REPORT.md) for findings and the
remaining Core changes. The team will discuss and review those changes. BE may promote this
contract to `docs/architecture/data-provenance-contract.md`, align
`docs/requirements-mapping.md`, and implement the separately reviewed Core changes.
Those promotions have not occurred. N1 is not DONE until role policy, Core
hardening, regression evidence and BE review all satisfy the detailed plan.
