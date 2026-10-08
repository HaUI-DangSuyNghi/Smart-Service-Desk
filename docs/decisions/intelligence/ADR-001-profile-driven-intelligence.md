# ADR-001: Profile-driven Intelligence

Status: **PROPOSED**, derived from the existing N1 capability design; BE and business review pending.

## Context

Generic triage/search envelopes have multiple Application/Intelligence consumers.
Service Desk taxonomy, routing and priority rules are domain configuration. Mixing
them in shared schemas or Core would couple new domains to Service Desk behavior.

## Decision

Place generic [contracts](../../../contracts/intelligence/v1/contract.md) at repository
level. Intelligence owns a versioned [profile manifest](../../../intelligence/profiles/service-desk/profile.yaml)
that composes taxonomy, rules, priority policy and normalization. Keep one
machine-readable priority matrix/fallback in `priority-policy.yaml`; the review
rule references that fallback. The Process Owner owns policy approval.

Profile-scoped [fixtures](../../../fixtures/intelligence/service-desk/tickets/manifest.json)
live outside the runtime boundary. Further domains add their own profile/fixtures.
Applications can consume Core alone. Engines remain behind shared capability
interfaces; no runtime registry/engine selection is implemented in N1.

## Consequences

Changes to approved policy require business review and a new profile version or a
reviewed later explicit policy version. Configuration versions identify immutable
content. Core retains generic identity/data/workflow/audit capabilities. N1 schema
reuse checks permit Room Request values without introducing a second runtime/profile.
