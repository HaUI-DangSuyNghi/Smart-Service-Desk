# ADR-002: Rules + TF-IDF MVP candidates

Status: **PROPOSED**, implementation/adoption pending after the Core and BE review gates.

## Context

N1 needs traceable triage and replaceable retrieval interfaces. Its ticket outputs
and retrieval gold are manual expected annotations. They do not demonstrate engine
execution, ranking quality, accuracy or benchmark performance.

## Decision

Retain the existing proposal: rules for the first triage approach and TF-IDF/cosine
as the first retrieval candidate. Neither is implemented, installed as a project
dependency or pinned by the profile manifest. See the
[candidate inventory](../governance/intelligence-dependency-candidates.md).

Use shared [capability contracts](../../../contracts/intelligence/v1/contract.md)
so BM25, classifiers, embeddings, hybrid or LLM engines can replace an approach
without changing Core authority. Engine-specific scores are ranking values,
not confidence/probabilities or comparable scales across engines.

## Consequences

Adoption needs dependency/license review, version pins, runtime configuration and
evaluation evidence. Future engines consume authorized Core data, scope indexes
before scoring, and record engine/profile/corpus/index versions. No FastAPI service,
engine, index, vector DB, embedding/model or runtime dependency is added by this refactor.
